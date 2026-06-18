package idusw.sbb.triplinker.domain.route.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.triplinker.domain.expense.entity.Expense;
import idusw.sbb.triplinker.domain.expense.repository.ExpenseRepository;
import idusw.sbb.triplinker.domain.place.service.PlaceService;
import idusw.sbb.triplinker.domain.plan.entity.PlanInputForm;
import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.plan.repository.TravelPlanRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class AiRouteService {

    private final TravelPlanRepository planRepository;
    private final ExpenseRepository expenseRepository;
    private final PlaceService placeService;
    private final ObjectMapper objectMapper;
    private final ChatClient primaryClient;   // Groq
    private final ChatClient fallbackClient;  // Gemini

    public AiRouteService(
            TravelPlanRepository planRepository,
            ExpenseRepository expenseRepository,
            PlaceService placeService,
            ObjectMapper objectMapper,
            @Qualifier("openAiChatModel") ChatModel groqModel,
            @Qualifier("googleGenAiChatModel") ChatModel geminiModel) {

        this.planRepository = planRepository;
        this.expenseRepository = expenseRepository;
        this.placeService = placeService;
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
            아래의 DB 데이터와 [필수 준수 사항]을 바탕으로 최적의 일별 여행 코스를 생성하세요.
            
            [여행 기본 정보]
            - 여행지: %s / 일정: %s ~ %s / 예산: %d원 / 인원: %s (%d명)
            - 이동 수단: %s / 숙소 유형: %s (옵션: %s) / 스타일: %s / 식이: %s / 밀도: %s
            - 유아 동반: %s / 반려동물 동반: %s
            - 유저 요청 사항: %s
            
            [필수 준수 사항 - 데이터 무결성 (위반 시 시스템 오류 발생)]
            1. 장소명 절대 규칙 (Hallucination 원천 차단):
               - 정보가 부족하더라도 장소를 임의로 지어내거나 합성하지 마십시오. (예: "OO먹자골목", "홍대 맛집" 등 모호한 명칭 금지)
               - 반드시 카카오맵(KakaoMap)에 공식적으로 등록되어 고유 식별이 가능한 [실존하는 대중적인 랜드마크, 상호명, 숙박업소명]만 정확히 기입하세요. (예: "제주신라호텔", "연돈")
               - 장소명 뒤에 '체크인', '방문', '식사' 같은 임의의 서술형 접미사를 절대 붙이지 마십시오. 이미 폐업했거나 가상의 장소는 내부 재검색을 통해 원천 배제하세요.
            
            2. 유저 요청 사항 최우선 반영:
               - [여행 기본 정보]의 '유저 요청 사항'에 특정 일자/메뉴/장소가 명시되어 있다면, AI의 자체적인 추천보다 이를 무조건 1순위로 강제 배치해야 합니다.
            
            3. 지역 일관성 및 동선 그룹화 (이동 최소화):
               - 모든 장소는 제공된 '여행지' 행정구역 내에 있어야 합니다.
               - 일별 코스를 짤 때, 지그재그 동선이 되지 않도록 같은 동네/권역 위주로 묶어서 배치하세요.
               - 직전 장소로부터 지정된 '이동 수단' 기준 너무 먼 거리(편도 30분 이상, 20km 이상)는 배치를 금지하며, 거리가 멀 경우 반경 5~10km 이내의 다른 장소로 내부 재검색(Re-search)을 수행해 일정을 재구성하세요.
            
            4. 시간 흐름 및 일정 밀도 반영:
               - 각 장소의 'time'은 아침-점심-오후-저녁 순서로 자연스럽게 흘러야 합니다.
               - '밀도' 설정(여유롭게/빼곡하게)을 반영하여 장소 간 체류 시간과 간격을 조절하세요.
            
            5. 예산 및 상세 정보(sub) 무결성:
               - 별점(stars): 실제 인터넷 평균 평점을 반영하여 "★★★★☆ 4.5" 형식으로 기입하세요.
               - 상세 정보(sub): "숙소 · ₩180,000", "맛집 · 저녁 · ₩8,000×2", "관광지 · 1h · ₩2,000×2" 규격을 엄격히 지키세요.
               - 예산 동기화: 각 장소의 'sub' 금액과 'transit' 금액의 총합은 해당 일차의 'budget'과 정확히 일치해야 합니다. 전체 누적 금액 또한 주어진 총 예산에 최대한 맞춰야 합니다.
            
            6. 숙소(stay) 중복 배치 절대 금지:
               - 숙소(type: "stay")는 반드시 해당 일차(Day)의 맨 마지막 일정(저녁/밤)으로 **하루에 단 한 번만** 배치하세요.
               - 다음 날 아침 기상이나 체크아웃 명목으로 숙소를 아침 일정에 또 넣는 행위를 절대 금지합니다. (아침 첫 일정은 무조건 관광지나 식당으로 시작하세요).
            
            7. 현실적인 단가 및 수학적 예산 검증:
               - 숙박비에 1박 70만원 같은 터무니없는 바가지 요금을 적지 마세요. 일반적인 시세(10~20만 원 선)를 반영하세요.
               - 각 일차의 'budget' 금액은 해당 일차의 모든 'sub' 금액과 'transit' 금액을 더한 값과 **수학적으로 완벽하게 일치**해야 합니다. 계산을 틀리지 마세요.
            
            
            [출력 규칙 - 매우 중요]
            1. 장소의 'type' 필드는 반드시 "stay"(숙소), "food"(맛집), "cafe"(카페), "tour"(관광지) 4가지 고정된 문자열 중 하나만 사용해야 합니다. ('sight', 'attraction' 등으로 임의 조작 금지)
            2. 'budget'은 해당 일차 총액(예: ₩182,000), 'stars'는 별점(예: ★★★★★ 4.8) 포맷을 지키세요.
            3. 장소와 장소 사이에는 반드시 이동 정보(`transit`) 객체를 포함해야 합니다. (예: "🚗 자차 · 12km · 약 20분 · ₩2,000")
            4. 부가적인 설명, 인사말, 마크다운 코드블럭 기호(```json 등)를 일절 포함하지 말고, 오직 아래 형식의 순수 JSON 배열(Array) 텍스트만 출력하세요.
            
            [
              {
                "day": 1,
                "label": "📅 Day 1 · 06/14 (토)",
                "budget": "₩184,000",
                "places": [
                  { "type": "stay", "icon": "🏨", "name": "제주신라호텔", "sub": "숙소 · ₩180,000", "stars": "★★★★★ 4.8", "key": "uniq1", "time": "13:00", "replacePh": "예: 더 저렴한 펜션으로 교체해줘" },
                  { "transit": "🚗 자차 · 12km · 약 20분 · ₩2,000" },
                  { "type": "food", "icon": "🍽️", "name": "동래할매파전", "sub": "맛집 · 점심 · ₩12,000×2", "stars": "★★★★☆ 4.2", "key": "uniq2", "time": "14:30", "replacePh": "예: 다른 맛집으로" }
                ]
              }
            ]
            """,
                plan.getDestination(), plan.getStartDate(), plan.getEndDate(),
                form.getBudget(), form.getCompanionType(), form.getCompanionCount(),
                form.getTransportType(), form.getAccommodationType(), form.getAccommodationOptions(),
                form.getTravelStyles(), form.getDietaryInfo(), form.getScheduleDensity(),
                form.getHasInfant() == 1 ? "O" : "X", form.getHasPet() == 1 ? "O" : "X",
                parseExtraNotesToPrompt(form.getExtraNotes())
        );

        String aiRouteJson = "[]";
        try {
            System.out.println("🔄 Groq 일정 생성 기동 중...");
            // 메인 Groq API 호출 시도
            aiRouteJson = primaryClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            System.out.println("⚠️ Groq 호출 실패, 제미나이(Gemini) 기동 시작: " + e.getMessage());
            try {
                // 실패 시 제미나이 작동
                aiRouteJson = fallbackClient.prompt().user(prompt).call().content();
            } catch (Exception ex) {
                // 둘 다 터졌을 때 방어용 더미 데이터 반환
                System.out.println("🚨 모든 LLM이 터져 방어용 데이터를 리턴합니다.");

                String dest = plan.getDestination();
                aiRouteJson = String.format("""
                    [
                      {
                        "day": 1,
                        "label": "🚨 AI 응답 지연 (임시 데이터) · %s",
                        "budget": "₩130,000",
                        "places": [
                          {
                            "type": "food",
                            "icon": "🍽️",
                            "name": "%s 맛집",
                            "sub": "[서버 지연 임시] · 점심 · ₩15,000×2",
                            "stars": "☆☆☆☆☆ 0.0",
                            "key": "fallback_d1_1",
                            "time": "12:00",
                            "replacePh": "새로고침을 해주세요"
                          },
                          { "transit": "🚗 이동 · 약 15분 · ₩2,000" },
                          {
                            "type": "cafe",
                            "icon": "☕",
                            "name": "%s 카페",
                            "sub": "[서버 지연 임시] · ₩8,000×2",
                            "stars": "☆☆☆☆☆ 0.0",
                            "key": "fallback_d1_2",
                            "time": "14:00",
                            "replacePh": "새로고침을 해주세요"
                          },
                          { "transit": "🚗 이동 · 약 20분 · ₩3,000" },
                          {
                            "type": "tour",
                            "icon": "📍",
                            "name": "%s 관광지",
                            "sub": "[서버 지연 임시] · 1h · ₩10,000×2",
                            "stars": "☆☆☆☆☆ 0.0",
                            "key": "fallback_d1_3",
                            "time": "16:00",
                            "replacePh": "새로고침을 해주세요"
                          },
                          { "transit": "🚗 이동 · 약 30분 · ₩5,000" },
                          {
                            "type": "stay",
                            "icon": "🏨",
                            "name": "%s 숙소",
                            "sub": "[서버 지연 임시] · ₩55,000",
                            "stars": "☆☆☆☆☆ 0.0",
                            "key": "fallback_d1_4",
                            "time": "18:00",
                            "replacePh": "새로고침을 해주세요"
                          }
                        ]
                      }
                    ]
                    """, dest, dest, dest, dest, dest);
            }
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
        placeService.parseAndSavePlacesFromRouteJson(plan, json);
    }

    // AI 동선 JSON을 파싱해 가계부(Expense)의 "AI 예상 비용"을 생성/갱신
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
                            .user(plan.getUser())
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

    // "숙소 · ₩180,000" 또는 "맛집 · 점심 · ₩12,000×2" 형태에서 금액 추출
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

    // AI 부분 교체 전용 로직
    @Transactional
    public String replaceAiRoutePlaces(Long tripId, java.util.List<java.util.Map<String, String>> requests) {
        TravelPlan plan = planRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));

        PlanInputForm form = plan.getForm();
        if (form == null) {
            throw new IllegalStateException("해당 플랜의 취향 정보가 DB에 없습니다.");
        }

        String originalJson = plan.getRouteJson();
        if (originalJson == null || originalJson.isBlank()) {
            originalJson = "[]";
        }


        // 🎯 [인텔리제이 콘솔 로그 정밀 분기] 무슨 오류인지 종류별로 모아서 상세 로깅
        System.out.println("======================== [동선 오차] ========================");
        System.out.println("▶ 플랜 ID: " + tripId + " (" + plan.getDestination() + " 여행)");
        System.out.println("▶ 발생한 검증 오류 분석 결과:");

        StringBuilder reqStr = new StringBuilder();

        // 장소 종류별 분류를 위한 임시 카운터
        int fakeCount = 0;
        int distanceCount = 0;

        for (java.util.Map<String, String> req : requests) {
            String place = req.get("place");
            String reason = req.get("req");

            // 거리에 관한 오류인지 문장 검사 ("km나 떨어져", "멉니다", "20km 이내" 매칭)
            if (reason != null && (reason.contains("km") || reason.contains("멉니다") || reason.contains("가까운"))) {
                distanceCount++;
                System.out.println("[거리 초과 오차 " + distanceCount + "] 장소: [" + place + "] | 상세 사유: " + reason);
            } else {
                fakeCount++;
                System.out.println("[가짜 장소 오류 " + fakeCount + "] 장소: [" + place + "] | 상세 사유: " + reason);
            }

            reqStr.append(String.format("- 타겟 장소: [%s] -> 변경 요구사항: %s\n", place, reason));
        }
        System.out.println("▶ 총 오차 집계 -> 가짜 장소: " + fakeCount + "건 / 동선 파괴(거리가 먼 장소): " + distanceCount + "건");
        System.out.println("────────────────────────────────────────────────────────────────");

        String prompt = String.format("""
            당신은 'TripLinker'의 여행 동선 수정 전문 AI입니다.
            아래 제공된 [여행 기본 정보]와 [원본 여행 동선 JSON]을 참고하여,
            사용자의 [교체 요청 사항]에 맞게 해당 장소만 부분 교체한 후 전체 JSON을 다시 출력해 주세요.
            
            [여행 기본 정보]
            - 여행지: %s
            - 인원 및 동행자: %s (%d명) / 유아 동반: %s / 반려동물 동반: %s
            - 총 예산: %d원
            - 선호 스타일: %s / 식이 옵션: %s
            - 이동 수단: %s / 숙소 유형: %s
            - 유저 요청 사항: %s
            
            [교체 요청 사항]
            %s
            
            [원본 여행 동선 JSON]
            %s
            
            [부분 교체 필수 준수 사항 - 위반 시 시스템 오류 발생]
            1. 원본 보존의 원칙: 교체를 요청받은 타겟 장소를 제외한 다른 일차, 다른 장소, 시간, 배열 구조는 단 한 글자도 건드리지 말고 100%% 똑같이 유지하십시오.
            2. 주변 동선(Transit) 자동 업데이트: 장소가 교체되어 위치가 바뀌었으므로, 교체된 장소의 직전과 직후에 있는 'transit' 블록(거리, 소요 시간, 비용 등)도 새로운 위치 간의 실제 거리에 맞게 현실적으로 재계산해서 수정해 주세요.
            3. 장소명 절대 규칙 (Hallucination 원천 차단):
               - 정보가 부족하더라도 장소를 임의로 지어내거나 합성하지 마십시오.
               - 반드시 카카오맵(KakaoMap)에 공식적으로 등록되어 고유 식별이 가능한 [실존하는 대중적인 랜드마크, 상호명, 숙박업소명]만 정확히 기입하세요. (예: "제주신라호텔", "연돈")
               - 장소명 뒤에 '체크인', '방문', '식사' 같은 임의의 서술형 접미사를 절대 붙이지 마십시오. 폐업/가상의 장소는 원천 배제하세요.
            4. 예산 및 상세 정보(sub) 무결성:
               - 별점(stars): 실제 인터넷 평균 평점을 반영하여 "★★★★☆ 4.5" 형식으로 기입하세요.
               - 교체된 장소의 금액('sub' 필드)은 "숙소 · ₩180,000", "맛집 · 저녁 · ₩8,000×2" 규격을 엄격히 지키세요.
               - 장소가 바뀌면서 비용이 달라졌다면, 해당 일차의 총액인 'budget' 값도 반드시 새롭게 재계산하여 업데이트 하세요.
            5. 유저 요청 사항 강제 반영: [교체 요청 사항] 및 [여행 기본 정보]에 명시된 유저의 요구는 당신의 자체 추천보다 무조건 1순위로 강제 반영해야 합니다.
            
            
            [출력 규칙 - 매우 중요]
            1. 장소의 'type' 필드는 반드시 "stay"(숙소), "food"(맛집), "cafe"(카페), "tour"(관광지) 4가지 고정된 문자열 중 하나만 사용해야 합니다. ('sight', 'attraction' 등으로 임의 조작 금지)
            2. 'budget'은 해당 일차 총액(예: ₩182,000), 'stars'는 별점(예: ★★★★★ 4.8) 포맷을 지키세요.
            3. 장소와 장소 사이에는 반드시 이동 정보(`transit`) 객체를 포함해야 합니다. (예: "🚗 자차 · 12km · 약 20분 · ₩2,000")
            4. 부가적인 설명, 인사말, 마크다운 코드블럭 기호(```json 등)를 일절 포함하지 말고, 오직 아래 형식의 순수 JSON 배열(Array) 텍스트만 출력하세요.
            """,
                plan.getDestination(),
                form.getCompanionType(), form.getCompanionCount(),
                form.getHasInfant() == 1 ? "O" : "X", form.getHasPet() == 1 ? "O" : "X",
                form.getBudget(),
                form.getTravelStyles(), form.getDietaryInfo(),
                form.getTransportType(), form.getAccommodationType(),
                parseExtraNotesToPrompt(form.getExtraNotes()),
                reqStr.toString(), originalJson
        );

        String updatedJson = "[]";
        try {
            System.out.println("🔄 Groq 부분 교체 기동 중...");
            // 메인 Groq API 호출 시도
            updatedJson = primaryClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            System.out.println("⚠️ Groq 호출 실패, 제미나이(Gemini) 기동 시작: " + e.getMessage());
            try {
                // 실패 시 제미나이 작동
                updatedJson = fallbackClient.prompt().user(prompt).call().content();
            } catch (Exception ex) {
                // 둘 다 터졌을 때 방어용 더미 데이터 반환
                System.out.println("🚨 모델 전체 셧다운. 부분 교체를 취소하고 원본을 반환합니다.");
                return originalJson;
            }
        }

        if (updatedJson != null && updatedJson.contains("[")) {
            updatedJson = updatedJson.substring(updatedJson.indexOf("["), updatedJson.lastIndexOf("]") + 1);
        }

        String finalJson = updatedJson != null ? updatedJson.trim() : originalJson;

        if (!finalJson.equals(originalJson)) {
            saveAiRouteToDb(tripId, finalJson);
        }

        return finalJson;
    }

    // 기상 악화 특정 일차 실내 일정 전면 교체
    @Transactional
    public String replaceDayWithIndoor(Long tripId, int targetDay) {
        TravelPlan plan = planRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));

        PlanInputForm form = plan.getForm();
        String originalJson = plan.getRouteJson();
        if (originalJson == null || originalJson.isBlank()) {
            originalJson = "[]";
        }

        String prompt = String.format("""
            당신은 'TripLinker'의 여행 동선 수정 전문 AI입니다.ㅇ
            현재 사용자의 여행 일정 중 [Day %d]에 비/악천후 예보가 있습니다.
            아래 제공된 [여행 기본 정보]와 [원본 여행 동선 JSON]을 분석하여, 오직 "day": %d 에 해당하는 일정의 모든 야외 활동을 [100%% 실내 활동]으로 전면 교체해 주세요.
            
            [여행 기본 정보]
            - 여행지: %s
            - 인원 및 동행자: %s (%d명) / 유아 동반: %s / 반려동물 동반: %s
            - 총 예산: %d원
            - 선호 스타일: %s / 식이 옵션: %s
            - 이동 수단: %s / 숙소 유형: %s
            - 유저 요청 사항: %s
            
            [우천 대비 실내 대체 필수 지침 - ★최우선 반영★]
            1. 타겟 일차(Day %d)의 'tour'(관광지)는 무조건 비를 피할 수 있는 '실내 관광지(박물관, 아쿠아리움, 실내 테마파크, 대형 복합문화공간, 대형 카페 등)'로만 재구성하십시오.
            2. 원본 보존의 원칙: 타겟 일차(Day %d)를 제외한 나머지 다른 Day의 일정은 단 한 글자도 건드리지 말고 100%% 원본과 똑같이 유지하십시오.
            3. 주변 동선(Transit) 자동 업데이트: 장소가 교체되어 위치가 바뀌었으므로, 직전/직후 'transit'(이동 시간 및 비용)을 해당 이동 수단에 맞는 현실적인 수치로 재계산하여 업데이트하십시오.
            4. 장소명 절대 규칙 (Hallucination 원천 차단):
               - 정보가 부족하더라도 장소를 임의로 지어내거나 합성하지 마십시오.
               - 반드시 카카오맵(KakaoMap)에 공식적으로 등록되어 고유 식별이 가능한 [실존하는 대중적인 랜드마크, 상호명, 숙박업소명]만 정확히 기입하세요. (예: "아르떼뮤지엄 제주", "코엑스 아쿠아리움")
               - 장소명 뒤에 '체크인', '방문', '식사' 같은 임의의 서술형 접미사를 절대 붙이지 마십시오. 폐업/가상의 장소는 원천 배제하세요.
            5. 예산 및 상세 정보(sub) 무결성:
               - 별점(stars): 실제 인터넷 평균 평점을 반영하여 "★★★★☆ 4.5" 형식으로 기입하세요.
               - 교체된 장소의 금액('sub' 필드)은 "숙소 · ₩180,000", "관광지 · 2h · ₩15,000×2" 규격을 엄격히 지키세요.
               - 장소가 바뀌면서 비용이 달라졌다면, 해당 일차의 총액인 'budget' 값도 반드시 새롭게 재계산하여 업데이트 하세요.
            
            [원본 여행 동선 JSON]
            %s
            
            [출력 규칙 - 매우 중요]
            1. 장소의 'type' 필드는 반드시 "stay"(숙소), "food"(맛집), "cafe"(카페), "tour"(관광지) 4가지 고정된 문자열 중 하나만 사용해야 합니다.
            2. 부가적인 설명, 인사말, 마크다운 코드블럭 기호(```json 등)를 일절 포함하지 말고, 오직 [원본 여행 동선 JSON]과 동일한 구조의 순수 JSON 배열(Array) 텍스트만 출력하세요.
            """,
                targetDay, targetDay,
                plan.getDestination(),
                form.getCompanionType(), form.getCompanionCount(),
                form.getHasInfant() == 1 ? "O" : "X", form.getHasPet() == 1 ? "O" : "X",
                form.getBudget(),
                form.getTravelStyles(), form.getDietaryInfo(),
                form.getTransportType(), form.getAccommodationType(),
                parseExtraNotesToPrompt(form.getExtraNotes()),
                targetDay, targetDay,
                originalJson
        );

        String updatedJson = "[]";
        try {
            System.out.println("☔ 우천 대비 실내 일정 교체 (Day " + targetDay + ") 기동 중...");
            // 메인 Groq API 호출 시도
            updatedJson = primaryClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            System.out.println("⚠️ Groq 호출 실패, 제미나이(Gemini) 기동 시작: " + e.getMessage());
            try {
                // 실패 시 제미나이 작동
                updatedJson = fallbackClient.prompt().user(prompt).call().content();
            } catch (Exception ex) {
                // 둘 다 터졌을 때 방어용 더미 데이터 반환
                System.out.println("🚨 모델 전체 셧다운. 부분 교체를 취소하고 원본을 반환합니다.");
                return originalJson;
            }
        }

        if (updatedJson != null && updatedJson.contains("[")) {
            updatedJson = updatedJson.substring(updatedJson.indexOf("["), updatedJson.lastIndexOf("]") + 1);
        }

        String finalJson = updatedJson != null ? updatedJson.trim() : originalJson;

        if (!finalJson.equals(originalJson)) {
            saveAiRouteToDb(tripId, finalJson);
        }

        return finalJson;
    }


    // extra_notes JSON을 AI가 알아볼 수 있도록 수정
    private String parseExtraNotesToPrompt(String extraNotesJson) {
        if (extraNotesJson == null || extraNotesJson.trim().isEmpty() || !extraNotesJson.startsWith("[")) {
            return "없음";
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            java.util.List<java.util.Map<String, String>> notesList = objectMapper.readValue(
                    extraNotesJson, new TypeReference<>() {}
            );

            java.util.StringJoiner sj = new java.util.StringJoiner(", ");
            for (java.util.Map<String, String> note : notesList) {
                String label = note.get("label");
                String value = note.get("value");
                if (label != null && value != null) {
                    sj.add(label + ": " + value);
                }
            }

            return sj.length() > 0 ? sj.toString() : "없음"; // 결과 예시: "1일차저녁: 회"
        } catch (Exception e) {
            return "없음";
        }
    }
}