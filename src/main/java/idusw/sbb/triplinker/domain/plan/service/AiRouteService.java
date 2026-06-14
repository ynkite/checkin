package idusw.sbb.triplinker.domain.plan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.triplinker.domain.expense.entity.Expense;
import idusw.sbb.triplinker.domain.expense.repository.ExpenseRepository;
import idusw.sbb.triplinker.domain.plan.entity.PlanInputForm;
import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.plan.repository.TravelPlanRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class AiRouteService {

    private final TravelPlanRepository planRepository;
    private final ExpenseRepository expenseRepository;
    private final ObjectMapper objectMapper;
    private final ChatClient primaryClient;   // Groq
    private final ChatClient fallbackClient;  // Gemini

    public AiRouteService(
            TravelPlanRepository planRepository,
            ExpenseRepository expenseRepository,
            ObjectMapper objectMapper,
            @Qualifier("openAiChatModel") ChatModel groqModel,
            @Qualifier("googleGenAiChatModel") ChatModel geminiModel) {

        this.planRepository = planRepository;
        this.expenseRepository = expenseRepository;
        this.objectMapper = objectMapper;
        this.primaryClient  = ChatClient.builder(groqModel).build();
        this.fallbackClient = ChatClient.builder(geminiModel).build();
    }

    public String generateAiRoute(Long tripId) {
        TravelPlan plan = planRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));
        PlanInputForm form = plan.getForm();

        if (form == null) {
            throw new IllegalStateException("해당 플랜의 취향 정보가 DB에 없습니다.");
        }

        String prompt = String.format("""
            당신은 'TripLinker'의 동선 생성 전문 AI입니다.
            아래의 DB 데이터를 바탕으로 최적의 일별 여행 코스를 생성하세요.
            
            [여행 기본 정보]
            - 여행지: %s / 일정: %s ~ %s / 예산: %d원 / 인원: %s (%d명)
            - 이동 수단: %s / 숙소 유형: %s (옵션: %s) / 스타일: %s / 식이: %s / 밀도: %s
            
            [출력 규칙 - 매우 중요]
            카카오맵 연동을 위해 각 장소의 실제 위도(lat)와 경도(lng)를 소수점 4자리까지 기입하세요.
            'budget'에는 해당 일차 총액(예: ₩182,000), 'stars'에는 별점(예: ★★★★★ 4.8 · 네이버맵), 'sub'에는 상세 정보(예: 숙소 · ₩180,000)를 규격화하여 넣으세요.
            다른 설명, 인사말, 마크다운 기호(```json) 없이 오직 아래 형식의 순수 JSON 배열만 출력하세요.
            
            [
              {
                "day": 1,
                "label": "📅 Day 1 · 06/14 (토)",
                "budget": "₩182,000",
                "places": [
                  { "type": "stay", "icon": "🏨", "name": "제주신라호텔 체크인", "sub": "숙소 · ₩180,000", "stars": "★★★★★ 4.8 · 네이버맵", "key": "uniq1", "lat": 33.2481, "lng": 126.4116, "time": "13:00", "replacePh": "예: 더 저렴한 펜션으로 교체해줘" },
                  { "transit": "🚗 자차 · 12km · 약 20분 · ₩2,000" },
                  { "type": "tour", "icon": "📍", "name": "성산일출봉", "sub": "관광지 · 1h · ₩2,000×2", "stars": "★★★★★ 4.9", "key": "uniq2", "lat": 33.4582, "lng": 126.9426, "time": "15:00", "replacePh": "예: 실내 관광지로" }
                ]
              }
            ]
            """,
                plan.getDestination(), plan.getStartDate(), plan.getEndDate(),
                form.getBudget(), form.getCompanionType(), form.getCompanionCount(),
                form.getTransportType(), form.getAccommodationType(), form.getAccommodationOptions(),
                form.getTravelStyles(), form.getDietaryInfo(), form.getScheduleDensity(),
                form.getHasInfant() == 1 ? "O" : "X", form.getHasPet() == 1 ? "O" : "X"
        );

        String aiRouteJson;
        try {
            aiRouteJson = primaryClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            aiRouteJson = fallbackClient.prompt().user(prompt).call().content();
        }

        // 앞뒤 마크다운 잔해 제거 가드 코드
        if (aiRouteJson != null && aiRouteJson.contains("[")) {
            aiRouteJson = aiRouteJson.substring(aiRouteJson.indexOf("["), aiRouteJson.lastIndexOf("]") + 1);
        }

        return aiRouteJson != null ? aiRouteJson.trim() : "[]";
    }

    @Transactional
    public void saveAiRouteToDb(Long tripId, String json) {
        TravelPlan plan = planRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));

        plan.setRouteJson(json);
        plan.setRouteRecalcNeeded(0);
        planRepository.save(plan);

        parseAndSaveEstimatedExpenses(plan, json);
    }

    private void parseAndSaveEstimatedExpenses(TravelPlan plan, String json) {
        // 재생성 시 기존 AI 예상 비용 초기화
        expenseRepository.deleteEstimatedByPlanId(plan.getId());

        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) return;

            for (JsonNode dayNode : root) {
                int day = dayNode.path("day").asInt(1);
                LocalDate expenseDate = plan.getStartDate().plusDays(day - 1);
                JsonNode places = dayNode.path("places");
                if (!places.isArray()) continue;

                for (JsonNode place : places) {
                    if (!place.has("type")) continue; // transit 항목 스킵

                    String category = mapTypeToCategory(place.path("type").asText(""));
                    if (category == null) continue;

                    long amount = parseAmountFromSub(place.path("sub").asText(""));
                    if (amount <= 0) continue;

                    expenseRepository.save(Expense.builder()
                            .plan(plan)
                            .category(category)
                            .description(place.path("name").asText(""))
                            .amount(amount)
                            .isEstimated(true)
                            .expenseDate(expenseDate)
                            .build());
                }
            }
        } catch (Exception e) {
            System.err.println("[Expense] AI 예상 비용 파싱 실패: " + e.getMessage());
        }
    }

    private String mapTypeToCategory(String type) {
        return switch (type.toLowerCase()) {
            case "stay"  -> "STAY";
            case "food"  -> "FOOD";
            case "tour"  -> "TOUR";
            case "cafe"  -> "CAFE";
            default      -> null;
        };
    }

    // "숙소 · ₩180,000" 또는 "관광지 · ₩2,000×2" 형태에서 금액 추출
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("₩([\\d,]+)(?:×(\\d+))?");

    private long parseAmountFromSub(String sub) {
        Matcher m = AMOUNT_PATTERN.matcher(sub);
        if (!m.find()) return 0L;
        long amount = Long.parseLong(m.group(1).replace(",", ""));
        if (m.group(2) != null) amount *= Long.parseLong(m.group(2));
        return amount;
    }

    public Object getRoutesByTripId(Long tripId) {
        TravelPlan plan = planRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));

        return plan.getRouteJson();
    }
}