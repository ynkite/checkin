package idusw.sbb.triplinker.domain.route.service;

import idusw.sbb.triplinker.domain.plan.entity.PlanInputForm;
import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.plan.repository.TravelPlanRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AiRouteService {

    private final TravelPlanRepository planRepository;
    private final ChatClient primaryClient;   // Groq
    private final ChatClient fallbackClient;  // Gemini

    public AiRouteService(
            TravelPlanRepository planRepository,
            @Qualifier("openAiChatModel") ChatModel groqModel,
            @Qualifier("googleGenAiChatModel") ChatModel geminiModel) {

        this.planRepository = planRepository;
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
            
            [필수 준수 사항 - 데이터 무결성 (위반 시 시스템 오류 발생)]
            1. 장소명(name) 엄격 통제 및 일치: 당신은 정보가 부족할 때 그럴듯한 장소를 지어내는(Hallucination) 치명적인 버그가 있습니다. 이를 방지하기 위해 "합천스카이밸리", "OO먹자골목", "OO서비스"처럼 모호하거나 합성된 가짜 이름을 절대 금지합니다. 또한 장소명 뒤에 '체크인', '방문', '식사' 같은 임의의 서술형 접미사를 붙이지 마십시오. 무조건 검색 포털(카카오맵/네이버 지도)에 공식적으로 등록된 [실존하는 유명 랜드마크, 실제 유명 식당 상호명, 실제 숙박업소명]만 엄선해서 정확히 기입하세요. (예: "제주신라호텔", "성산일출봉")
            2. 별점(stars): 당신이 가진 지식 베이스를 바탕으로, 해당 장소의 실제 인터넷 평균 평점을 계산하여 "★★★★☆ 4.5" 형식의 문자열로 반드시 입력하세요.
            3. 상세 정보(sub): 장소의 성격에 맞는 실제 예상 가격과 단위 정보를 아래 규격에 맞춰 명확히 기입하세요.
               - 숙소 예시: "숙소 · ₩180,000"
               - 맛집 예시: "맛집 · 저녁 · ₩8,000×2"
               - 관광지 예시: "관광지 · 1h · ₩2,000×2"
            4. 예산 동기화: 각 장소들의 'sub'에 기입된 금액의 총합은 해당 일차의 'budget'과 완벽히 일치해야 합니다. 또한, 전체 일정의 총 누적 금액은 제공된 [여행 기본 정보]의 '예산' 범위 이내(최대한 예산에 가깝게 대등한 수준)로 맞춰야 합니다.
            5. 지역 일관성: 제공된 [여행 기본 정보]의 여행지(행정구역 시/군) 내에 실제로 존재하는 장소만 배치해야 합니다. (예: 제주도 여행이면 부산 장소가 포함되어서는 절대 안 됨)
            
            [출력 규칙 - 매우 중요]
            1. 장소의 'type' 필드는 반드시 "stay"(숙소), "food"(맛집), "cafe"(카페), "tour"(관광지) 이 4가지 고정된 문자열 중 하나만 사용해야 합니다. (관광지를 'sight'나 'attraction' 등으로 임의 조작 절대 금지)
            2. 'budget'에는 해당 일차 총액(예: ₩182,000), 'stars'에는 별점(예: ★★★★★ 4.8), 'sub'에는 상세 정보(예: 숙소 · ₩180,000)를 규격화하여 넣으세요.
            3. 다른 설명, 인사말, 마크다운 기호(```json) 없이 오직 아래 형식의 순수 JSON 배열만 출력하세요.
            
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
                form.getHasInfant() == 1 ? "O" : "X", form.getHasPet() == 1 ? "O" : "X"
        );

        String aiRouteJson = "[]";
        try {
            // 메인 Groq API 호출 시도
            aiRouteJson = primaryClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            System.out.println("⚠️ Groq 호출 실패, 제미나이(Gemini) 기동 시작: " + e.getMessage());
            try {
                // 실패 시 제미나이 폴백 작동
                aiRouteJson = fallbackClient.prompt().user(prompt).call().content();
            } catch (Exception ex) {
                // 둘 다 터졌을 때 방어용 더미 데이터 반환
                System.out.println("🚨 모든 LLM이 터져 방어용 데이터를 리턴합니다.");
                aiRouteJson = String.format("[{\"day\": 1, \"label\": \"📅 Day 1 · %s\", \"budget\": \"₩0\", \"places\": []}]", plan.getDestination());
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
    }

    public Object getRoutesByTripId(Long tripId) {
        TravelPlan plan = planRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));

        return plan.getRouteJson();
    }

    // AI 부분 교체 전용 로직
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

        // 사용자가 수정한 교체 요청 목록을 텍스트로 합치기
        StringBuilder reqStr = new StringBuilder();
        for (java.util.Map<String, String> req : requests) {
            reqStr.append(String.format("- 타겟 장소: [%s] -> 변경 요구사항: %s\n", req.get("place"), req.get("req")));
        }

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
            
            [교체 요청 사항]
            %s
            
            [원본 여행 동선 JSON]
            %s
            
            [출력 규칙 - 매우 중요]
            1. 장소의 'type' 필드는 반드시 "stay"(숙소), "food"(맛집), "cafe"(카페), "tour"(관광지) 이 4가지 고정된 문자열 중 하나만 사용해야 합니다. (관광지를 'sight'나 'attraction' 등으로 임의 조작 절대 금지)
            2. 'budget'에는 해당 일차 총액(예: ₩182,000), 'stars'에는 별점(예: ★★★★★ 4.8), 'sub'에는 상세 정보(예: 숙소 · ₩180,000)를 규격화하여 넣으세요.
            3. 다른 설명, 인사말, 마크다운 기호(```json) 없이 오직 아래 형식의 순수 JSON 배열만 출력하세요.
            
            [필수 준수 사항]
            1. 요청받은 타겟 장소 외의 다른 일차, 다른 장소, 시간, 배열 구조는 **절대 건드리지 마십시오**. 100%% 똑같이 유지해야 합니다.
            2. 🚗 동선(Transit) 자동 업데이트: 장소가 교체되어 위치가 바뀌었으므로, **교체된 장소의 직전과 직후에 있는 'transit' 블록(거리, 소요 시간, 비용 등)도 새로운 위치 간의 실제 거리에 맞게 현실적으로 재계산해서 수정**해 주세요.
            3. 교체되는 새로운 장소는 카카오맵/네이버 지도에 등록된 실존하는 상호명이어야 합니다. (예: "애견호텔" (X) -> "마이펫 애견호텔 광안점" (O))
            4. 교체된 장소의 금액('sub' 필드)은 현실적인 물가로 책정하고, 비용이나 거리가 변경되었다면 해당 일차의 전체 'budget' 값도 반드시 새롭게 재계산하여 업데이트 하세요.
            5. 마크다운 기호(```json) 없이 오직 순수한 JSON 배열 포맷만 출력하세요.
            
            
            """,
                plan.getDestination(),
                form.getCompanionType(), form.getCompanionCount(),
                form.getHasInfant() == 1 ? "O" : "X", form.getHasPet() == 1 ? "O" : "X",
                form.getBudget(),
                form.getTravelStyles(), form.getDietaryInfo(),
                form.getTransportType(), form.getAccommodationType(),
                reqStr.toString(), originalJson
        );

        String updatedJson = "[]";
        try {
            System.out.println("🔄 Groq 부분 교체 기동 중...");
            updatedJson = primaryClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            System.out.println("⚠️ Groq 부분 교체 실패, 제미나이 폴백: " + e.getMessage());
            try {
                updatedJson = fallbackClient.prompt().user(prompt).call().content();
            } catch (Exception ex) {
                System.out.println("🚨 모델 전체 셧다운. 부분 교체를 취소하고 원본을 반환합니다.");
                return originalJson;
            }
        }

        if (updatedJson != null && updatedJson.contains("[")) {
            updatedJson = updatedJson.substring(updatedJson.indexOf("["), updatedJson.lastIndexOf("]") + 1);
        }

        return updatedJson != null ? updatedJson.trim() : originalJson;
    }
}