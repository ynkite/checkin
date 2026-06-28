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

/*
 * ============================================================================
 *  경로 생성 AI 파이프라인 안내
 * ----------------------------------------------------------------------------
 *  [신규 동작]
 *   1) Groq 가 1차 여행 경로(JSON)를 생성한다.            ← generateAiRoute()
 *   2) Claude 가 그 결과를 검증하고 오류(가짜 장소·예산 불일치·동선 파괴 등)를
 *      바로잡아 최종 일정을 만든다.                        ← validateAndFixWithClaude()
 *   3) 지도에서 장소를 교체할 때는 기존처럼 Groq 가 처리한다. ← replaceAiRoutePlaces()
 *
 *  ※ 엔진 전환은 아래 3가지 주석 표시를 기준으로 한다.
 *    // 클로드 API 사용할 때          → Anthropic 정식 SDK(anthropicChatModel)
 *    // open api 사용할 때            → Claude를 OpenAI 호환 엔드포인트로 사용 (선택)
 *    // 기존 API(그록 재미나이)사용할 때 → Claude 검증 단계를 끄고 Groq 결과를 그대로 사용
 *
 *  ▶ Claude가 안 될 때(키 미발급/장애 등):
 *    generateAiRoute() 안의 "Claude 검증" 호출부를 주석 처리하면
 *    Groq(+Gemini 폴백)만으로 예전과 동일하게 경로가 생성된다.
 *    (validateAndFixWithClaude() 내부에도 Claude 실패 시 원본 그대로 반환하는
 *     안전장치가 있어 코드 수정 없이도 자동으로 기존 결과가 유지된다.)
 * ============================================================================
 */
@Service
@Transactional(readOnly = true)
public class AiRouteService {

    private final TravelPlanRepository planRepository;
    private final ExpenseRepository expenseRepository;
    private final PlaceService placeService;
    private final ObjectMapper objectMapper;

    // ★카카오 거리 계산용
    private final org.springframework.web.client.RestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Value("${kakao.rest.api.key}")
    private String kakaoRestKey;

    // ── AI 클라이언트 ─────────────────────────────────────────────
    private final ChatClient claudeClient;    // Claude (검증·교정 담당)  ★ NEW
    private final ChatClient primaryClient;   // Groq (1차 생성 / 장소 교체)
    private final ChatClient fallbackClient;  // Gemini (폴백)

    public AiRouteService(
            TravelPlanRepository planRepository,
            ExpenseRepository expenseRepository,
            PlaceService placeService,
            ObjectMapper objectMapper,
            org.springframework.web.client.RestTemplate restTemplate,

            // ===== // 클로드 API 사용할 때 (Anthropic 정식 SDK) =====
            @Qualifier("anthropicChatModel") ChatModel claudeModel,
            // ===== // 클로드 API 사용할 때 끝 =====

            // ===== // open api 사용할 때 (Claude를 OpenAI 호환 엔드포인트로) =====
            //  application.yml 에서 OpenAI starter base-url 을 Anthropic 으로 돌린 뒤,
            //  아래 빈을 claudeModel 대신 주입해 사용한다.
            // @Qualifier("openAiChatModel") ChatModel claudeOpenAiModel,
            // ===== // open api 사용할 때 끝 =====

            // ===== // 기존 API(그록 재미나이)사용할 때 =====
            @Qualifier("openAiChatModel") ChatModel groqModel,
            @Qualifier("googleGenAiChatModel") ChatModel geminiModel) {
        // ===== // 기존 API(그록 재미나이)사용할 때 끝 =====

        this.planRepository = planRepository;
        this.expenseRepository = expenseRepository;
        this.placeService = placeService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;

        // ===== // 클로드 API 사용할 때 =====
        this.claudeClient   = ChatClient.builder(claudeModel).build();
        // ===== // 클로드 API 사용할 때 끝 =====

        // ===== // open api 사용할 때 =====
        // this.claudeClient   = ChatClient.builder(claudeOpenAiModel).build();
        // ===== // open api 사용할 때 끝 =====

        // ===== // 기존 API(그록 재미나이)사용할 때 =====
        this.primaryClient  = ChatClient.builder(groqModel).build();
        this.fallbackClient = ChatClient.builder(geminiModel).build();
        // ===== // 기존 API(그록 재미나이)사용할 때 끝 =====
    }

    public String generateAiRoute(Long tripId) {
        TravelPlan plan = planRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));

        PlanInputForm form = plan.getForm();
        if (form == null) {
            throw new IllegalStateException("해당 플랜의 취향 정보가 DB에 없습니다.");
        }

        // ★당일치기 판별: 시작일==종료일(0박)이면 숙소를 절대 넣지 않는다
        boolean isDayTrip = plan.getStartDate() != null && plan.getStartDate().equals(plan.getEndDate());
        String stayRule = isDayTrip
                ? """
              ★★★당일치기 — 숙소 절대 금지★★★: 이 여행은 0박 당일치기입니다.
              어떤 Day에도 "type":"stay"(숙소)를 단 하나도 넣지 마십시오. 숙소를 넣으면 시스템 오류로 간주합니다.
              관광지·맛집·카페로만 하루 일정을 구성하고, 밤에 숙소 복귀도 넣지 마세요.
              """
                : """
              숙소(stay) 배치 규칙:
                 - 첫째 날(Day 1)과 마지막 날을 제외한 중간 날은, 전날 묵은 동일 숙소를 그 날 '맨 첫 일정(아침)'과 '맨 마지막 일정(밤)'에 모두 배치합니다.
                 - 첫째 날(Day 1)은 아침에 숙소를 넣지 말고 관광지·식당으로 시작하고, 그 날 밤에 숙소를 배치합니다.
                 - 마지막 날은 아침에 전날 숙소에서 출발(체크아웃)하되, 밤에는 새 숙소를 넣지 않습니다.
                 - 같은 날 안에서 '서로 다른' 숙소를 두 개 이상 넣는 것은 금지합니다(동일 숙소를 출발/취침으로 두 번 넣는 것은 위 규칙대로 허용).
              """;

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
               - 정보가 부족하더라도 절대 장소를 임의로 지어내거나 합성하지 마십시오. (예: "OO먹자골목", "홍대 맛집" 등 모호한 명칭 절대 금지)
               - 반드시 카카오맵(KakaoMap)에 공식적으로 등록되어 고유 식별이 가능한 [실존하는 대중적인 랜드마크, 상호명, 숙박업소명]만 정확히 기입하세요. (예: "제주신라호텔", "연돈")
               - ★숙박업소 이름 규칙★: 숙소명은 반드시 카카오맵에 등록된 공식 상호명 전체를 그대로 쓰세요.
                 "함안호텔"처럼 지역명+업종만 붙인 임의 합성 명칭은 절대 금지입니다.
                 실존하는 호텔·펜션·게스트하우스의 고유 상호명(예: "함안아라가야호텔", "함안문화게스트하우스")을 사용하세요.
                 - ★숙소 업종 일치★: '숙소 유형'이 "호텔"이면 정식 호텔만 배치하고 모텔·여관·게스트하우스를
                                              호텔 대신 넣지 마세요. "펜션"이면 펜션만, "리조트"면 리조트만 배치하세요.
               - 장소명 뒤에 '체크인', '방문', '식사' 같은 임의의 서술형 접미사를 절대 붙이지 마십시오. 이미 폐업했거나 가상의 장소는 내부 재검색을 통해 원천 배제하세요.
            
            2. 유저 요청 사항 최우선 반영:
               - [여행 기본 정보]의 '유저 요청 사항'에 특정 일자/메뉴/장소가 명시되어 있다면, AI의 자체적인 추천보다 이를 무조건 1순위로 강제 배치해야 합니다.
            
            3. 지역 일관성 및 동선 그룹화 (이동 최소화):
                - ★거리 "수치 출력"만 금지 (위치 "판단"은 반드시 하라)★:
                  ┌ [금지] transit의 거리(km)·소요시간(분)·교통비(원)를 JSON에 숫자로 적는 것. 시스템이
                  │        카카오맵 길찾기 API로 실제값을 자동으로 채우므로, transit 객체는 항상
                  │        "transit": "이동" 한 가지로만 출력하세요. (km·분·금액 절대 적지 마세요)
                  └ [필수] 단, 출력만 하지 않을 뿐 "장소들이 지리적으로 어디 있는지" 머릿속 판단은
                           반드시 하세요. 각 장소의 대략적 위치를 떠올려 가까운 순서로 정렬하는 사고
                           과정은 동선 품질의 핵심입니다. "계산하지 말라"는 말은 "JSON에 숫자를 쓰지
                           말라"는 뜻이지, "위치를 무시하고 아무 순서로나 나열하라"는 뜻이 절대 아닙니다.
                           당신은 장소의 선택과 '지리적으로 올바른 순서'를 책임집니다.
               - 모든 장소는 제공된 '여행지' 행정구역 내에 있어야 합니다.
               - ★절대 금지★: '여행지'가 "제주도"이면 제주도 밖(서울·대전·부산·강릉 등 타지역)의
                 장소를 단 하나도 넣지 마십시오. 모든 Day의 모든 장소는 반드시 '여행지' 시/도 경계 안에만
                 존재해야 합니다. 타지역 장소가 섞이면 시스템 오류로 간주합니다.
               - ★동선 클러스터링(최우선)★: 같은 날(Day)의 장소들은 서로 10km 이내에 모여 있도록
                 배치하세요. 후보가 여러 곳이면 같은 날 다른 장소들과 가장 가까운 곳을 선택하십시오.
                 단, [유저 요청 사항]에서 사용자가 특정 장소·지역을 명시적으로 요청한 경우에만 10km 제약을 예외로 허용합니다.
               - 일별 코스를 짤 때, 지그재그 동선이 되지 않도록 같은 동네/권역 위주로 묶어서 배치하세요.
               - ★날짜별 구역 분리(2일 이상)★: 숙소를 기준으로 날마다 노는 구역을 나누세요.
                 예) 1일차는 숙소 기준 한쪽 방향(북/동 등)의 장소들로, 2일차는 반대 방향(남/서 등)의
                 장소들로 묶어, 날짜가 바뀔 때마다 같은 지역을 왕복하지 않도록 하세요. 각 날의 장소들은
                 그 날의 구역 안에서 서로 가깝게 모읍니다(숙소 복귀 구간 제외).
               - 직전 장소로부터 지정된 '이동 수단' 기준 너무 먼 거리(편도 30분 이상, 20km 이상)는 배치를 금지하며, 거리가 멀 경우 반경 5~10km 이내의 다른 장소로 내부 재검색(Re-search)을 수행해 일정을 재구성하세요.
               - ★이동 시간 상한(매우 중요 최종 점검도 한번 더 할 것 절대적으로 효율적인 동선 배치 지그재그로 왔다갔다 동선 금지)★: 
               같은 날 장소 간 이동은 지정된 '이동 수단' 기준 편도 30분 이내가 되도록 배치하세요.
                (자차는 약 15~20km, 대중교통은 약 8~10km 이내). 사용자가 특정 장소를 콕 집어 요청한 경우에만 예외.
                단, 그 날 마지막에 숙소로 복귀하는 구간은 1시간까지는 허용합니다.
               - ★동선 정렬 알고리즘(반드시 이 순서로 사고하라)★:
                 (1) 먼저 그 날 방문할 후보 장소들의 대략적인 위경도(지리적 위치)를 머릿속에 떠올린다.
                 (2) 출발 지점(전날 숙소 또는 첫 장소)에서 시작해, 항상 '직전 장소에서 가장 가까운 다음 장소'를
                     골라 한 방향으로만 이동하는 최근접 이웃(Nearest-Neighbor) 순서로 장소를 나열한다.
                 (3) 이미 지나온 방향으로 되돌아가는(역주행) 배치는 절대 금지. 한 번 동→서로 흐르기 시작했으면
                     끝까지 그 흐름을 유지하고, 다시 동쪽으로 튀어 올라가지 마라.(사용자가 교체나 정확하게 요청한 경우 제외)
                 (4) 장소 순서를 지리적으로 다 확정한 뒤에, 그 순서에 맞춰 아침→점심→오후→저녁 time을 배정하라.
                     (시간을 먼저 정하고 장소를 끼워 넣지 말 것)
                 (5) 최종 출력 직전, 같은 날 장소들의 순서를 위치 기준으로 한 번 더 점검하라.
                     (숫자를 적으라는 게 아니라, 머릿속으로 "이 순서로 가면 한 방향으로 흐르는가,
                      되돌아가는 구간은 없는가"만 확인하는 것이다.) 역주행하거나 멀리 튀는 구간이
                     보이면 순서를 바꿔 더 짧은 동선으로 재배치한 뒤 출력하라. (transit 값은 그래도 "이동"만 적는다)
              - ★★★숙소 연속성(최우선·중복금지 규칙보다 우선)★★★★: 2일 이상 여행이면, 둘째 날(Day 2)부터는
                                          전날 묵은 동일 숙소(같은 name의 stay)를 그 날의 '맨 첫 번째 일정(time 아침)'으로 배치해 그곳에서 하루를 시작하고,
                                          마지막 날을 제외한 각 Day는 그 동일 숙소를 '맨 마지막 일정'으로도 배치해 취침합니다.
                                          즉 중간 날(Day 2 ~ 마지막 전날)은 동일 숙소가 그 날 안에서 [맨 처음=출발] 과 [맨 끝=취침] 두 번 등장해야 정상입니다.
                                          이때 등장하는 동일 숙소는 아래 '숙소 중복 금지' 규칙의 예외이며, 절대 다른 숙소로 교체하지 마세요.
            
            3-1. ★장소 중복 금지★ (사용자가 별도 요청하지 않은 경우):
               - 같은 장소(동일 상호명)는 전체 일정에서 단 한 번만 등장해야 합니다.
               - 예: 쌍계사를 Day 1에 배치했다면 Day 2에 쌍계사를 다시 넣지 마세요.
               - 숙소(stay)도 마찬가지입니다. 단, 위 '숙소 연속성' 규칙에 따라 전날 숙소를 다음 날 출발지로 재등장시키는 것은 정상이며 허용합니다(이 경우만 중복 금지의 예외).
               - 중복이 발생하면 같은 권역의 다른 실존 장소로 대체하세요.
            
            4. 시간 흐름 및 일정 밀도 반영:
               - 각 장소의 'time'은 아침-점심-오후-저녁 순서로 자연스럽게 흘러야 합니다.
               - '밀도' 설정(여유롭게/빼곡하게)을 반영하여 장소 간 체류 시간과 간격을 조절하세요.
            
            5. 예산 및 상세 정보(sub) 무결성:
               - 별점(stars): ★절대 임의로 별점을 지어내지 마세요★. 실제 평점 데이터를 모르면
                 반드시 "평점 정보 없음"이라고만 적으세요. 추측하거나 그럴듯한 숫자를 만들어내는 것은 금지입니다.
                 (시스템이 TripLinker 커뮤니티/카카오맵 실제 평점이 있으면 그 값으로 덮어씁니다.)
                 따라서 stars 필드는 항상 "평점 정보 없음"으로 출력하세요.
               - 상세 정보(sub): "숙소 · ₩180,000", "맛집 · 저녁 · ₩8,000×2", "관광지 · 1h · ₩2,000×2" 규격을 엄격히 지키세요.
               - 예산 동기화: 각 장소의 'sub' 금액과 'transit' 금액의 총합은 해당 일차의 'budget'과 정확히 일치해야 합니다. 전체 누적 금액 또한 주어진 총 예산에 최대한 맞춰야 합니다.
               - ★★★예산 최우선 준수 규칙★★★: 사용자가 입력한 총 예산(%d원)은 반드시 지켜야 하는 상한선입니다.
                 전체 일정의 누적 합계(모든 Day의 budget 총합)가 이 총 예산의 90~100%% 범위 안에 들어오도록
                 장소 단가·숙소 등급·식당 가격대를 조절해서 맞추세요. 예산을 초과하면 안 됩니다.
               - ★예산 현실성 판단★: 사용자 예산이 인원수·여행 일수 대비 명백히 불가능한 수준
                 (예: 2인 1박2일에 1만원처럼 1인 1일 최소 식비·교통비조차 안 되는 금액)이 아닌 한,
                 사용자가 제시한 예산은 '합리적인 금액'으로 간주하고 그 예산에 맞춰 일정을 완성하세요.
                 20만원·30만원처럼 충분히 여행이 가능한 금액이면 "예산이 적다"는 식의 상향 요구나
                 더 비싼 장소로의 교체를 절대 하지 말고, 그 예산 안에서 가성비 좋은 실존 장소들로
                 알차게 동선을 구성하세요. 예산이 빠듯하면 무료·저가 관광지와 가성비 식당·숙소를 우선 배치합니다.
            
                        6. %s
            
                        7. ★식당·장소 명칭 구체화(포괄 명칭 금지)★:
                                       - "○○시장", "○○ 먹자골목", "○○ 거리", "○○ 맛집촌" 같은 포괄·집합 명칭을 맛집(food)으로
                                         넣지 마세요. 반드시 그 안에 실존하는 개별 식당의 정확한 상호명을 적고, 'sub' 필드에
                                         대표 메뉴와 1인 가격을 함께 명시하세요. (예: "맛집 · 점심 · 돼지국밥 ₩9,000×2")
                                       - 특정 식당을 확신할 수 없으면 시장/거리 명칭 대신 그 지역의 일반적인 실존 식당 한 곳을
                                         골라 동일한 규격(상호명 + 대표메뉴 + 가격)으로 채우세요. 포괄 명칭으로 얼버무리지 마세요.

                        8. 현실적인 단가:
                                       - 숙박비·식비·입장료는 일반적인 시세를 반영하세요. 특히 숙박비에 1박 70만원 같은 터무니없는 바가지 요금을 적지 마세요 
                                       (호텔 기준 10~20만 원 선).
            
            
            [출력 규칙 - 매우 중요]
            1. 장소의 'type' 필드는 반드시 "stay"(숙소), "food"(맛집), "cafe"(카페), "tour"(관광지) 4가지 고정된 문자열 중 하나만 사용해야 합니다. ('sight', 'attraction' 등으로 임의 조작 금지)
            2. 'budget'은 해당 일차 총액(예: ₩182,000)을 지키고, 'stars'는 임의로 만들지 말고 항상 "평점 정보 없음"으로 출력하세요.
            3. 장소와 장소 사이에는 반드시 이동 정보(`transit`) 객체를 포함하되, 값은 항상 "이동"으로만 적으세요. (예: { "transit": "이동" }) 거리·시간·금액은 시스템이 채웁니다.
            4. 부가적인 설명, 인사말, 마크다운 코드블럭 기호(```json 등)를 일절 포함하지 말고, 오직 아래 형식의 순수 JSON 배열(Array) 텍스트만 출력하세요.
            [
              {
                "day": 1,
                "label": "📅 Day 1 · 06/14 (토)",
                "budget": "₩184,000",
                "places": [
                  { "type": "stay", "icon": "🏨", "name": "제주신라호텔", "sub": "숙소 · ₩180,000", "stars": "평점 정보 없음", "key": "uniq1", "time": "13:00", "replacePh": "예: 더 저렴한 펜션으로 교체해줘" },
                  { "transit": "이동" },
                  { "type": "food", "icon": "🍽️", "name": "동래할매파전", "sub": "맛집 · 점심 · ₩12,000×2", "stars": "평점 정보 없음", "key": "uniq2", "time": "14:30", "replacePh": "예: 다른 맛집으로" }
                ]
              }
            ]
            """,
                plan.getDestination(), plan.getStartDate(), plan.getEndDate(),
                form.getBudget(), form.getCompanionType(), form.getCompanionCount(),
                form.getTransportType(), form.getAccommodationType(), form.getAccommodationOptions(),
                form.getTravelStyles(), form.getDietaryInfo(), form.getScheduleDensity(),
                form.getHasInfant() == 1 ? "O" : "X", form.getHasPet() == 1 ? "O" : "X",
                parseExtraNotesToPrompt(form.getExtraNotes()),
                form.getBudget(),
                stayRule
        );

        String aiRouteJson = "[]";
        try {
//            System.out.println("🔄 Groq 일정 생성 기동 중...");
            System.out.println("🔄 Claude 일정 생성 기동 중...");
            // 메인 Groq API 호출 시도
//            aiRouteJson = primaryClient.prompt().user(prompt).call().content();
            // 메인을 클로드로 변경
            aiRouteJson = claudeClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
//            System.out.println("⚠️ Groq 호출 실패, 제미나이(Gemini) 기동 시작: " + e.getMessage());
            System.out.println("⚠️ Claude 호출 실패, Groq 기동 시작: " + e.getMessage());
            try {
                // 실패 시 제미나이 작동
//                aiRouteJson = fallbackClient.prompt().user(prompt).call().content();
                // 실패 시 그록 작동
                aiRouteJson = primaryClient.prompt().user(prompt).call().content();
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
            Matcher jsonStartM = Pattern.compile("\\[\\s*\\{").matcher(aiRouteJson);
            int jsonStart = jsonStartM.find() ? jsonStartM.start() : aiRouteJson.indexOf("[");
            aiRouteJson = aiRouteJson.substring(jsonStart, aiRouteJson.lastIndexOf("]") + 1);
        }

        String groqResultJson = (aiRouteJson != null) ? aiRouteJson.trim() : "[]";

        // ─────────────────────────────────────────────────────────────
        //  2단계: Claude 가 Groq 결과를 검증하고 오류를 교정
        // ===== // 클로드 API 사용할 때 =====
        //   ▶ Claude를 끄고 Groq 결과를 그대로 쓰려면(=기존 방식 유지) 아래 한 줄을
        //     주석 처리하면 된다. 그러면 곧바로 'return groqResultJson;' 으로 떨어진다.
        // String finalRouteJson = validateAndFixWithClaude(plan, form, groqResultJson);
        // return finalRouteJson;
        return groqResultJson;   // 실제로는 위의 Claude 1차 결과
        // ===== // 클로드 API 사용할 때 끝 =====

        // ===== // 기존 API(그록 재미나이)사용할 때 =====
        //   위 Claude 검증 블록(두 줄)을 주석 처리하고 아래 한 줄을 살리면
        //   Groq(+Gemini 폴백) 결과를 검증 없이 그대로 사용한다.
        // return groqResultJson;
        // ===== // 기존 API(그록 재미나이)사용할 때 끝 =====
    }

    /*
     * Claude 검증·교정 단계
     *  - Groq 가 만든 경로 JSON을 받아 [데이터 무결성 / 예산 수학 / 동선 / 가짜 장소]를
     *    점검하고 문제가 있으면 고쳐서 같은 구조의 JSON으로 되돌려준다.
     *  - Claude 호출이 실패하면(키 없음/장애 등) 원본(Groq 결과)을 그대로 반환하므로,
     *    이 메서드를 살려둬도 Claude가 죽으면 자동으로 기존 결과가 유지된다.
     */
    private String validateAndFixWithClaude(TravelPlan plan, PlanInputForm form, String groqRouteJson) {
        if (groqRouteJson == null || groqRouteJson.isBlank() || !groqRouteJson.contains("[")) {
            return groqRouteJson;
        }
        // Groq·Gemini 둘 다 실패해 방어용 더미가 들어온 경우엔 검증하지 않고 그대로 반환
        if (groqRouteJson.contains("fallback_d1_") || groqRouteJson.contains("서버 지연 임시")) {
            return groqRouteJson;
        }

        String prompt = String.format("""
            당신은 'TripLinker'의 여행 일정 검수(QA) 전문 AI입니다.
            다른 AI가 1차로 생성한 [원본 여행 동선 JSON]을 받아, 아래 [검증 항목]을 기준으로
            오류가 있는 부분만 정확히 수정한 뒤, 동일한 구조의 완성된 JSON 배열만 출력하세요.
            오류가 전혀 없다면 원본을 그대로(구조 변경 없이) 다시 출력하세요.

            [여행 기본 정보]
            - 여행지: %s / 일정: %s ~ %s / 예산: %d원 / 인원: %s (%d명)
            - 이동 수단: %s / 숙소 유형: %s / 스타일: %s / 식이: %s / 밀도: %s
            - 유아 동반: %s / 반려동물 동반: %s
            - 유저 요청 사항: %s

            [검증 항목 - 발견 시 반드시 교정]
            1. 가짜 장소 차단: 카카오맵에 실존하지 않거나 모호한 명칭("OO먹자골목", "XX 맛집"),
               폐업/가상의 장소, '체크인/방문/식사' 같은 임의 접미사가 붙은 장소는
               같은 권역의 실존하는 대중적 장소로 교체하세요.
               ★숙박명 검증★: "함안호텔"처럼 지역명+업종만 붙인 임의 합성 명칭은 카카오맵에 없는
               가짜 이름으로 간주하고, 해당 지역에 실제 등록된 숙박업소의 공식 상호명으로 교체하세요.
            2. 지역 일관성: 모든 장소가 '여행지' 행정구역 안에 있어야 합니다. 벗어난 장소는 교체.
            3. 동선 무결성: 편도 30분/20km 이상 멀리 튀는 지그재그 동선이면 반경 5~10km 내
               장소로 교체하고, 직전/직후 'transit'(거리·시간·비용)을 현실적으로 재계산하세요.
               ★클러스터링★: 같은 날 장소들이 10km 이상 흩어져 지그재그가 생기면,
               다른 장소들과 가장 가까운 실존 장소로 교체하여 하루 동선을 한 권역으로 모으세요.
               단, 유저 요청에 명시된 특정 장소는 교체 금지.
                        4. ★장소 중복 제거★: 전체 일정에서 같은 장소(동일 상호명)가 2번 이상 등장하면,
                                       두 번째 등장하는 것을 같은 권역의 다른 실존 장소로 교체하세요. 단, 유저가 재방문을 요청한 경우는 예외.
                                       ★숙소 연속성 예외★: 전날 묵은 동일 숙소(stay)가 다음 날 '맨 첫 일정(출발)'으로 다시 등장하는 것은 정상적인 연속성이므로 절대 중복으로 간주해 교체·삭제하지 마세요.
                                    5. ★숙소 연속성 검증★: 2일 이상 일정에서 중간 날(Day 2 ~ 마지막 전날)은 전날 숙소가 그 날 '맨 처음(아침 출발)'과 '맨 마지막(밤 취침)'에 모두 있어야 정상입니다.
                                       - 빠져 있으면 전날과 동일한 숙소 stay 객체를 그 위치에 추가하세요.
                                       - 단, 첫째 날(Day 1) 아침과 마지막 날 밤에는 숙소를 넣지 마세요. (Day 1은 관광/식당으로 시작, 마지막 날은 아침에 전날 숙소에서 출발 후 밤 숙소 없음)
            6. 예산 수학 검증(가장 중요): 각 Day의 'budget' 은 그 Day의 모든 'sub' 금액과
               'transit' 금액의 합과 정확히 일치해야 합니다. 틀리면 budget을 올바르게 다시 계산하세요.
               숙박비가 비현실적(1박 70만원 등)이면 일반 시세(10~20만원)로 보정하세요.
            7. 타입 규칙: 'type' 은 "stay","food","cafe","tour" 4가지만 허용. 그 외 값은 알맞게 교정.

            [원본 여행 동선 JSON]
            %s

            [출력 규칙 - 매우 중요]
            1. 'type' 은 반드시 "stay","food","cafe","tour" 중 하나.
            2. 장소와 장소 사이에는 반드시 이동 정보('transit') 객체를 포함.
            3. 인사말·설명·마크다운 코드블럭(```json 등) 금지. 오직 원본과 동일한 구조의
               순수 JSON 배열(Array) 텍스트만 출력하세요.
            """,
                plan.getDestination(), plan.getStartDate(), plan.getEndDate(),
                form.getBudget(), form.getCompanionType(), form.getCompanionCount(),
                form.getTransportType(), form.getAccommodationType(),
                form.getTravelStyles(), form.getDietaryInfo(), form.getScheduleDensity(),
                form.getHasInfant() == 1 ? "O" : "X", form.getHasPet() == 1 ? "O" : "X",
                parseExtraNotesToPrompt(form.getExtraNotes()),
                groqRouteJson
        );

        String fixedJson;
        try {
            System.out.println("🔎 Claude 일정 검증·교정 기동 중...");
            fixedJson = claudeClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            // Claude 실패 시 → 검증 생략하고 Groq 원본을 그대로 사용 (기존 방식 유지)
            System.out.println("⚠️ Claude 검증 실패, Groq 원본 그대로 사용: " + e.getMessage());
            return groqRouteJson;
        }

        if (fixedJson == null || !fixedJson.contains("[")) {
            return groqRouteJson;
        }

        // 마크다운 잔해 제거
        fixedJson = fixedJson.substring(fixedJson.indexOf("["), fixedJson.lastIndexOf("]") + 1).trim();

        // 교정 결과가 유효한 JSON 배열인지 한 번 더 방어 (깨졌으면 원본 사용)
        try {
            JsonNode parsed = objectMapper.readTree(fixedJson);
            if (!parsed.isArray() || parsed.isEmpty()) {
                return groqRouteJson;
            }
        } catch (Exception e) {
            return groqRouteJson;
        }

        return fixedJson;
    }

    @Transactional
    public void saveAiRouteToDb(Long tripId, String json) {
        TravelPlan plan = planRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));

        // ★카카오 API로 거리/시간/통행료를 실제값으로 보정 (저장 직전 1회)
        PlanInputForm form = plan.getForm();
        json = recalcTransitWithKakao(json, form != null ? form.getTransportType() : null, plan.getDestination());

        // ★당일치기(0박)면 숙소(stay)를 강제 제거 (AI가 규칙 어겨도 최종 차단)
        if (plan.getStartDate() != null && plan.getStartDate().equals(plan.getEndDate())) {
            json = stripStayForDayTrip(json);
        }

        // ★미확정 종료 시 직전 확정본 보존:
        //   - 이미 확정(FIXED)된 플랜을 다시 저장 = '수정 중' → draft에만 저장(확정본 routeJson 유지)
        //   - 아직 확정 전(DRAFT 등) = 신규 생성 흐름 → 확정본 routeJson에 바로 저장
        boolean isEditingConfirmed = "FIXED".equals(plan.getStatus());
        if (isEditingConfirmed) {
            plan.setDraftRouteJson(json);   // 확정본(routeJson)은 건드리지 않는다
        } else {
            plan.setRouteJson(json);
        }
        plan.setRouteRecalcNeeded(0);
        planRepository.save(plan);

        // 예상비용·장소 미리보기는 현재 보고 있는 일정(draft 우선) 기준으로 갱신해도 무방하다.
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

        // 수정 중(draft)이 있으면 그걸 보여주고, 없으면 확정본을 보여준다.
        String draft = plan.getDraftRouteJson();
        if (draft != null && !draft.isBlank()) return draft;
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

        // ★거부 이력 수집: 이번에 교체 요청된 장소들(=이미 사용자가 거부한 장소)을
        //   "다시 넣지 말라" 블랙리스트로 만들어 프롬프트에 전달한다.
        StringBuilder rejectedStr = new StringBuilder();
        for (java.util.Map<String, String> req : requests) {
            String place = req.get("place");
            if (place != null && !place.isBlank()) {
                rejectedStr.append("- ").append(place.trim()).append("\n");
            }
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
            
            [★재교체 금지 장소(블랙리스트)★]
            아래 장소들은 사용자가 이미 거부한 장소이므로 절대로 다시 추천하면 안 됩니다.
            교체 결과에 아래 이름이 단 하나라도 다시 등장하면 시스템 오류로 간주합니다.
            반드시 아래 목록에 없는, 같은 권역의 '다른' 실존 장소로 교체하세요.
            %s
            
            [원본 여행 동선 JSON]
            %s
            
            [부분 교체 필수 준수 사항 - 위반 시 시스템 오류 발생]
            0-A. ★재교체 절대 금지★: 위 [재교체 금지 장소(블랙리스트)]에 있는 장소는 어떤 경우에도
                 결과에 다시 넣지 마십시오. A를 거부해 B가 나왔고 B도 거부됐다면, A로 되돌아가지 말고
                 반드시 A·B 둘 다 아닌 제3의 실존 장소를 찾으세요.
            0. ★지역 절대 잠금★: 교체로 새로 넣는 모든 장소는 반드시 여행지 "%s" 행정구역 안에 실존해야 합니다.
               여행지가 "경북 청송군"이면 청송군(또는 인접 30km 이내) 밖의 장소(서울·대전·부산·제주 등)는
               절대 넣지 마십시오. 교체 요청에 "권역을 벗어났다"는 사유가 있으면, 그 장소를 반드시
               여행지 권역 안의 다른 실존 장소로 바꿔야 합니다. 이 규칙은 다른 모든 규칙보다 우선합니다.
            1. 원본 보존의 원칙: 교체를 요청받은 타겟 장소를 제외한 다른 일차, 다른 장소, 시간, 배열 구조는 단 한 글자도 건드리지 말고 100%% 똑같이 유지하십시오.
            2. 주변 동선(Transit) 자동 업데이트: 장소가 교체되어 위치가 바뀌었으므로, 교체된 장소의 직전과 직후에 있는 'transit' 블록(거리, 소요 시간, 비용 등)도 새로운 위치 간의 실제 거리에 맞게 현실적으로 재계산해서 수정해 주세요.
            3. 장소명 절대 규칙 (Hallucination 원천 차단):
               - 정보가 부족하더라도 장소를 임의로 지어내거나 합성하지 마십시오.
               - 반드시 카카오맵(KakaoMap)에 공식적으로 등록되어 고유 식별이 가능한 [실존하는 대중적인 랜드마크, 상호명, 숙박업소명]만 정확히 기입하세요. (예: "제주신라호텔", "연돈")
               - 장소명 뒤에 '체크인', '방문', '식사' 같은 임의의 서술형 접미사를 절대 붙이지 마십시오. 폐업/가상의 장소는 원천 배제하세요.
            4. 예산 및 상세 정보(sub) 무결성:
               - 별점(stars): ★절대 임의로 별점을 지어내지 마세요★. 실제 평점을 모르면 반드시 "평점 정보 없음"으로만 적으세요. (시스템이 실제 평점이 있으면 덮어씁니다)
               - 교체된 장소의 금액('sub' 필드)은 "숙소 · ₩180,000", "맛집 · 저녁 · ₩8,000×2" 규격을 엄격히 지키세요.
               - 장소가 바뀌면서 비용이 달라졌다면, 해당 일차의 총액인 'budget' 값도 반드시 새롭게 재계산하여 업데이트 하세요.
            5. 유저 요청 사항 강제 반영: [교체 요청 사항] 및 [여행 기본 정보]에 명시된 유저의 요구는 당신의 자체 추천보다 무조건 1순위로 강제 반영해야 합니다.
            6. ★동선 클러스터링(우선 사항)★: 교체로 새로 넣는 장소는 가능한 한 그 날(같은 day)의 다른 장소들과
               서로 10km 이내에 모여 있도록 최우선으로 배치하세요. 후보가 여러 곳이면 다른 장소들과 가장
               가까운 곳을 선택하십시오. 단, [교체 요청 사항]이나 '유저 요청 사항'에서 사용자가 특정 거리·지역을
               명시적으로 예외 요청한 경우에는 10km 제약보다 사용자 요청을 우선하여 예외로 허용합니다.
            
            [출력 규칙 - 매우 중요]
            1. 장소의 'type' 필드는 반드시 "stay"(숙소), "food"(맛집), "cafe"(카페), "tour"(관광지) 4가지 고정된 문자열 중 하나만 사용해야 합니다. ('sight', 'attraction' 등으로 임의 조작 금지)
            2. 'budget'은 해당 일차 총액(예: ₩182,000)을 지키고, 'stars'는 임의로 만들지 말고 항상 "평점 정보 없음"으로 출력하세요.
            3. 장소와 장소 사이에는 반드시 이동 정보(`transit`) 객체를 포함하되, 값은 항상 "이동"으로만 적으세요. (예: { "transit": "이동" }) 거리·시간·금액은 시스템이 채웁니다.
            4. 부가적인 설명, 인사말, 마크다운 코드블럭 기호(```json 등)를 일절 포함하지 말고, 오직 아래 형식의 순수 JSON 배열(Array) 텍스트만 출력하세요.
            """,
                plan.getDestination(),
                form.getCompanionType(), form.getCompanionCount(),
                form.getHasInfant() == 1 ? "O" : "X", form.getHasPet() == 1 ? "O" : "X",
                form.getBudget(),
                form.getTravelStyles(), form.getDietaryInfo(),
                form.getTransportType(), form.getAccommodationType(),
                parseExtraNotesToPrompt(form.getExtraNotes()),
                reqStr.toString(),
                rejectedStr.length() > 0 ? rejectedStr.toString() : "- (없음)",  // ★블랙리스트 자리
                originalJson,
                plan.getDestination()   // 규칙 0의 ★지역 절대 잠금★ 자리
        );
        // groq 사용할때 지금은 전체 주석처리하고 아래 구문 사용
//        String updatedJson = "[]";
//        try {
//            System.out.println("🔄 Groq 부분 교체 기동 중...");
//            // [지도 장소 교체] 요구사항에 따라 이 단계는 Groq 가 담당한다.
//            // ===== // 기존 API(그록 재미나이)사용할 때 =====
//            // 메인 Groq API 호출 시도
//            updatedJson = primaryClient.prompt().user(prompt).call().content();
//            // ===== // 기존 API(그록 재미나이)사용할 때 끝 =====
//
//            // ===== // 클로드 API 사용할 때 (장소 교체도 Claude로 돌리고 싶다면) =====
//            //   위 Groq 호출을 주석 처리하고 아래 한 줄을 살리면 교체도 Claude가 수행한다.
//            //   (현재 요구사항은 '교체는 Groq' 이므로 기본은 주석 처리 상태)
//            // updatedJson = claudeClient.prompt().user(prompt).call().content();
//            // ===== // 클로드 API 사용할 때 끝 =====
//        } catch (Exception e) {
//            System.out.println("⚠️ Groq 호출 실패, 제미나이(Gemini) 기동 시작: " + e.getMessage());
//            try {
//                // 실패 시 제미나이 작동
//                updatedJson = fallbackClient.prompt().user(prompt).call().content();
//            } catch (Exception ex) {
//                // 둘 다 터졌을 때 방어용 더미 데이터 반환
//                System.out.println("🚨 모델 전체 셧다운. 부분 교체를 취소하고 원본을 반환합니다.");
//                return originalJson;
//            }
//        }
        String updatedJson = "[]";
        try {
            // [지도 장소 교체] 교체는 Claude 단독으로 수행한다.
            //   Groq/Gemini는 분당 토큰 한도(429)·과부하(503)가 잦아 교체 루프에서 원본이
            //   그대로 통과하는 사고가 있었으므로, 교체는 Claude만 사용한다.
            System.out.println("🤖 Claude 부분 교체 기동 중...");
            updatedJson = claudeClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            // Claude까지 실패하면 원본을 그대로 두지 말고, 검증에서 걸린 '가짜·타지역 장소'를
            // 일정에서 제거한 버전을 반환한다(가짜가 지도까지 새는 것을 막는 최종 안전장치).
            System.out.println("🚨 Claude 부분 교체 실패. 오류 장소를 일정에서 제외하고 반환합니다: " + e.getMessage());
            String stripped = stripBadPlaces(originalJson, requests);
            if (!stripped.equals(originalJson)) saveAiRouteToDb(tripId, stripped);
            return stripped;
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
    // 모든 모델이 실패했을 때, 검증에서 걸린 장소(requests의 place)를 일정에서 제거한다.
    // 가짜·타지역 장소가 그대로 지도까지 흘러가는 것을 막는 최종 안전장치.
    private String stripBadPlaces(String json, java.util.List<java.util.Map<String, String>> requests) {
        try {
            java.util.Set<String> bad = new java.util.HashSet<>();
            for (java.util.Map<String, String> r : requests) {
                String p = r.get("place");
                if (p != null && !p.isBlank()) bad.add(p.trim());
            }
            if (bad.isEmpty()) return json;

            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) return json;

            for (JsonNode dayNode : root) {
                JsonNode placesNode = dayNode.path("places");
                if (!placesNode.isArray()) continue;
                com.fasterxml.jackson.databind.node.ArrayNode places =
                        (com.fasterxml.jackson.databind.node.ArrayNode) placesNode;

                // 1) 가짜/타지역 장소 제거
                for (int i = places.size() - 1; i >= 0; i--) {
                    String name = places.get(i).path("name").asText("");
                    if (!name.isBlank() && bad.contains(name.trim())) places.remove(i);
                }
                // 2) 장소가 빠지며 붕 뜬 transit 정리(맨앞·맨뒤·연속 transit 제거)
                for (int i = places.size() - 1; i >= 0; i--) {
                    if (!places.get(i).has("transit")) continue;
                    boolean prevReal = i > 0 && places.get(i - 1).has("type");
                    boolean nextReal = i < places.size() - 1 && places.get(i + 1).has("type");
                    if (!(prevReal && nextReal)) places.remove(i);
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            System.err.println("[stripBadPlaces] 실패: " + e.getMessage());
            return json;
        }
    }
    // 당일치기(0박)일 때 숙소(stay) 객체와 붕 뜬 transit을 일정에서 제거
    private String stripStayForDayTrip(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) return json;
            for (JsonNode dayNode : root) {
                JsonNode placesNode = dayNode.path("places");
                if (!placesNode.isArray()) continue;
                com.fasterxml.jackson.databind.node.ArrayNode places =
                        (com.fasterxml.jackson.databind.node.ArrayNode) placesNode;
                // 1) 숙소 제거
                for (int i = places.size() - 1; i >= 0; i--) {
                    if ("stay".equals(places.get(i).path("type").asText(""))) places.remove(i);
                }
                // 2) 붕 뜬 transit 정리(맨앞·맨뒤·연속 transit 제거)
                for (int i = places.size() - 1; i >= 0; i--) {
                    if (!places.get(i).has("transit")) continue;
                    boolean prevReal = i > 0 && places.get(i - 1).has("type");
                    boolean nextReal = i < places.size() - 1 && places.get(i + 1).has("type");
                    if (!(prevReal && nextReal)) places.remove(i);
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            System.err.println("[stripStayForDayTrip] 실패: " + e.getMessage());
            return json;
        }
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
               - 별점(stars): ★절대 임의로 별점을 지어내지 마세요★. 실제 평점을 모르면 반드시 "평점 정보 없음"으로만 적으세요. (시스템이 실제 평점이 있으면 덮어씁니다)
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
//            updatedJson = primaryClient.prompt().user(prompt).call().content();
            // 메인 클로드 API 호출 시도
            updatedJson = claudeClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
//            System.out.println("⚠️ Groq 호출 실패, 제미나이(Gemini) 기동 시작: " + e.getMessage());
            System.out.println("⚠️ claude 호출 실패, Groq 기동 시작: " + e.getMessage());
            try {
                // 실패 시 제미나이 작동
//                updatedJson = fallbackClient.prompt().user(prompt).call().content();
                // 실패 시 그록 작동
                updatedJson = primaryClient.prompt().user(prompt).call().content();
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
    // ════════════════════════════════════════════════════════════════
    //  ★카카오 실측 거리 기반 동선 교정
    //   - 20/13km(숙소구간 35/25km) 초과 ~ 50km: '동떨어진 장소' 자동 교체(최대 2회)
    //   - 50km 초과: 자동 교체 안 하고, 그 장소 목록을 돌려줘서 프론트가 알림창 띄움
    //   - 사용자가 챗봇에서 직접 요청한 장소(extra_notes)는 교체 대상에서 제외(거리만 표시)
    // ════════════════════════════════════════════════════════════════

    /** 50km 초과 장소명 목록을 반환(없으면 빈 리스트). 동시에 50km 이하 초과 구간은 자동 교체한다. */
    /**
     * ★시간대 보존 정렬: 끼니(food)/숙소(stay)/사용자요청 장소는 고정하고,
     *   끼니 사이에 낀 tour·cafe만 거리순으로 미세 정렬해 지그재그를 줄인다.
     *   - 점심→저녁 같은 끼니 순서, 숙소 출발/취침 자리는 보존된다.
     *   - 단, 사용자가 직접 요청/입력한 장소(userRequested)는 stay/food가 아니어도 고정한다.
     *   - 카카오 길찾기 호출 없이 직선거리(haversine)만 사용 → 빠르고 토큰 0.
     *   반환: 재정렬 JSON (변화 없으면 원본).
     */
    private String reorderWithinTimeBlocks(String json, String destination,
                                           java.util.Set<String> userRequested) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) return json;
            java.util.Map<String, double[]> geo = new java.util.HashMap<>();

            for (JsonNode dayNode : root) {
                JsonNode placesNode = dayNode.path("places");
                if (!placesNode.isArray()) continue;

                java.util.List<JsonNode> spots = new java.util.ArrayList<>();
                for (JsonNode pl : placesNode) {
                    if (pl.has("transit") || !pl.has("name")) continue;
                    spots.add(pl);
                }
                int n = spots.size();
                if (n < 4) continue;

                java.util.Map<Integer, double[]> coord = new java.util.HashMap<>();
                for (int i = 0; i < n; i++) {
                    coord.put(i, geocodeCached(withRegion(destination, spots.get(i).path("name").asText("")), geo));
                }

                // 고정 = 숙소/끼니 또는 사용자가 직접 요청/입력한 장소
                java.util.function.IntPredicate isFixed = i -> {
                    String t = spots.get(i).path("type").asText("");
                    String nm = spots.get(i).path("name").asText("");
                    return "stay".equals(t) || "food".equals(t) || userRequested.contains(nm);
                };

                java.util.List<JsonNode> result = new java.util.ArrayList<>();
                int i = 0;
                while (i < n) {
                    result.add(spots.get(i));
                    if (!isFixed.test(i)) { i++; continue; }

                    // 고정점 다음의 연속된 '비고정' tour/cafe 구간 수집
                    int j = i + 1;
                    java.util.List<Integer> flex = new java.util.ArrayList<>();
                    while (j < n && !isFixed.test(j)) { flex.add(j); j++; }

                    if (flex.size() >= 2 && coord.get(i) != null) {
                        // 앞 고정점에서 최근접 이웃으로 정렬(좌표 없는 건 맨 뒤)
                        java.util.List<Integer> ordered = new java.util.ArrayList<>();
                        java.util.Set<Integer> used = new java.util.HashSet<>();
                        double[] cur = coord.get(i);
                        for (int k = 0; k < flex.size(); k++) {
                            int best = -1; double bestD = Double.MAX_VALUE;
                            for (int idx : flex) {
                                if (used.contains(idx)) continue;
                                double d = haversine(cur, coord.get(idx));
                                if (d < bestD) { bestD = d; best = idx; }
                            }
                            if (best < 0) break;
                            ordered.add(best); used.add(best); cur = coord.get(best);
                        }
                        for (int idx : flex) if (!used.contains(idx)) ordered.add(idx); // 좌표 null 잔여
                        for (int idx : ordered) result.add(spots.get(idx));
                    } else {
                        // 정렬 의미 없으면 원래 순서대로
                        for (int idx : flex) result.add(spots.get(idx));
                    }
                    i = j;
                }

                // 정렬된 장소 사이에 transit "이동" 재삽입(거리·시간은 카카오 보정이 채움)
                com.fasterxml.jackson.databind.node.ArrayNode rebuilt = objectMapper.createArrayNode();
                for (int k = 0; k < result.size(); k++) {
                    if (k > 0) {
                        com.fasterxml.jackson.databind.node.ObjectNode t = objectMapper.createObjectNode();
                        t.put("transit", "이동");
                        rebuilt.add(t);
                    }
                    rebuilt.add(result.get(k));
                }
                ((com.fasterxml.jackson.databind.node.ObjectNode) dayNode).set("places", rebuilt);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            System.err.println("[reorderWithinTimeBlocks] 실패, 원본 유지: " + e.getMessage());
            return json;
        }
    }

    /** 두 좌표[위도,경도] 간 직선거리(m). null이면 큰 값(맨 뒤로 밀림). */
    private double haversine(double[] a, double[] b) {
        if (a == null || b == null) return Double.MAX_VALUE / 2;
        double R = 6371000.0;
        double dLat = Math.toRadians(b[0] - a[0]);
        double dLon = Math.toRadians(b[1] - a[1]);
        double v = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(a[0]))*Math.cos(Math.toRadians(b[0]))
                * Math.sin(dLon/2)*Math.sin(dLon/2);
        return 2 * R * Math.asin(Math.sqrt(v));
    }

    public java.util.List<String> enforceDistanceAndGetOver50(Long tripId, String json) {
        TravelPlan plan = planRepository.findById(tripId).orElse(null);
        if (plan == null) return java.util.Collections.emptyList();
        PlanInputForm form = plan.getForm();
        final double HARD_LIMIT = 50_000; // 50km

        // 사용자가 직접 요청한 장소(교체 금지) 이름 모음
        java.util.Set<String> userRequested = extractUserRequestedNames(form);

        // ★0) LLM 없이 코드로 먼저 동선 순서를 최적화(시간대 보존 정렬).
        //    끼니/숙소/사용자요청 장소는 고정하고 tour·cafe만 끼니 사이에서 거리순 정렬.
        //    카카오 길찾기 호출 없이 직선거리만 쓰므로 빠르고 토큰 0.
        String reordered = reorderWithinTimeBlocks(json, plan.getDestination(), userRequested);
        if (!reordered.equals(json)) {
            saveAiRouteToDb(tripId, reordered);   // 내부 카카오 보정이 transit 숫자 채움
            json = String.valueOf(getRoutesByTripId(tripId));
        }

        // 1) 50km 초과 검사
        //    - 사용자가 직접 요청한 장소(userRequested)가 50km 초과 → 알림(over50)으로 프론트에 위임
        //    - AI가 넣은 장소가 50km 초과 → 알림 없이 자동 교체 대상(autoOver50)으로 분리
        java.util.List<String> over50 = new java.util.ArrayList<>();
        java.util.Set<String> autoOver50 = new java.util.HashSet<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            java.util.Map<String, double[]> geo = new java.util.HashMap<>();
            for (JsonNode dayNode : root) {
                JsonNode places = dayNode.path("places");
                if (!places.isArray()) continue;
                String prevName = null; double[] prevCoord = null; boolean prevStay = false;
                for (JsonNode pl : places) {
                    if (pl.has("transit") || !pl.has("name")) continue;
                    String nm = pl.path("name").asText("");
                    boolean stay = "stay".equals(pl.path("type").asText(""));
                    double[] c = geocodeCached(withRegion(plan.getDestination(), nm), geo);
                    // ★좌표를 못 찾은 장소(가짜·환각 의심)는 AI가 넣은 것이면 자동교체 대상에 포함
                    //   (사용자 요청 장소는 건드리지 않음)
                    if (c == null && !userRequested.contains(nm)) {
                        autoOver50.add(nm);
                    }
                    if (prevCoord != null && c != null) {
                        long[] r = carDirections(prevCoord, c);
                        if (r != null && r[0] > HARD_LIMIT) {
                            classifyOver50(nm, userRequested, over50, autoOver50);
                            if (prevName != null) classifyOver50(prevName, userRequested, over50, autoOver50);
                        }
                    }
                    prevName = nm; prevCoord = c; prevStay = stay;
                }
            }
        } catch (Exception e) {
            System.err.println("[over50 검사 실패] " + e.getMessage());
        }

        // AI 장소가 50km 초과면 알림 없이 즉시 자동 교체
        if (!autoOver50.isEmpty()) {
            java.util.List<java.util.Map<String, String>> reqs = new java.util.ArrayList<>();
            for (String nm : autoOver50) {
                java.util.Map<String, String> req = new java.util.HashMap<>();
                req.put("place", nm);
                req.put("req", "이 장소가 다른 일정들과 50km 이상 너무 멀리 떨어져 있습니다. 같은 권역의 가까운 다른 실존 장소로 교체해 주세요.");
                reqs.add(req);
            }
            try {
                replaceAiRoutePlaces(tripId, reqs);
            } catch (Exception e) {
                System.err.println("[50km 자동교체 실패] " + e.getMessage());
            }
        }

        // 사용자 요청 장소가 50km 초과면 자동교체하지 않고 프론트 알림에 위임
        if (!over50.isEmpty()) return over50;

        return java.util.Collections.emptyList();
    }
    /** 50km 초과 장소를 사용자요청(알림) / AI장소(자동교체)로 분류 */
    private void classifyOver50(String name, java.util.Set<String> userRequested,
                                java.util.List<String> over50, java.util.Set<String> autoOver50) {
        if (name == null || name.isBlank()) return;
        if (userRequested.contains(name)) {
            if (!over50.contains(name)) over50.add(name);   // 사용자 요청 → 알림
        } else {
            autoOver50.add(name);                            // AI 장소 → 자동 교체
        }
    }
    /** 기준 초과 구간이 있으면, 그날 '다른 장소들과 평균거리가 가장 먼' 장소를 교체 대상으로 골라 반환 */
    private java.util.List<java.util.Map<String, String>> findFarPlaces(
            String json, double normalLimit, double stayLimit, java.util.Set<String> userRequested, String destination) {
        java.util.List<java.util.Map<String, String>> result = new java.util.ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            java.util.Map<String, double[]> geo = new java.util.HashMap<>();

            for (JsonNode dayNode : root) {
                JsonNode placesNode = dayNode.path("places");
                if (!placesNode.isArray()) continue;

                java.util.List<String> names = new java.util.ArrayList<>();
                java.util.List<double[]> coords = new java.util.ArrayList<>();
                java.util.List<Boolean> stays = new java.util.ArrayList<>();
                for (JsonNode pl : placesNode) {
                    if (pl.has("transit") || !pl.has("name")) continue;
                    String nm = pl.path("name").asText("");
                    double[] c = geocodeCached(withRegion(destination, nm), geo);
                    if (c == null) continue;
                    names.add(nm); coords.add(c);
                    stays.add("stay".equals(pl.path("type").asText("")));
                }
                int n = names.size();
                if (n < 2) continue;

                // ★'그날 장소들끼리 15km 안에 뭉침' 검사 (haversine 직선거리, 카카오 호출 0)
                //   - 숙소를 뺀 비-숙소 장소들의 '중심점'을 구하고,
                //   - 그 중심에서 15km(=CLUSTER_RADIUS) 넘게 떨어진 비-숙소·비-사용자요청 장소를
                //     '흩어진 곳'으로 보고 모두 교체 대상에 넣는다(여러 개여도 한 번에).
                final double CLUSTER_RADIUS = 15_000.0;

                // ★중심은 '평균'이 아니라 '중앙값(median)'으로 잡는다.
                //   geocode가 한두 곳을 엉뚱한 타지역(수백 km 밖)으로 찍어도, 중앙값은
                //   그 이상치에 끌려가지 않아 '정상 장소들이 실제로 모인 지점'을 가리킨다.
                //   (평균을 쓰면 이상치 1개가 중심을 끌고가 멀쩡한 장소까지 흩어졌다고 오판함)
                java.util.List<Double> lats = new java.util.ArrayList<>();
                java.util.List<Double> lngs = new java.util.ArrayList<>();
                for (int i = 0; i < n; i++) {
                    if (stays.get(i)) continue;            // 숙소는 중심 계산에서 제외(외곽일 수 있음)
                    lats.add(coords.get(i)[0]); lngs.add(coords.get(i)[1]);
                }
                if (lats.size() < 2) continue;             // 비숙소 장소가 1개뿐이면 뭉침 판단 의미 없음
                java.util.Collections.sort(lats);
                java.util.Collections.sort(lngs);
                double[] center = { lats.get(lats.size()/2), lngs.get(lngs.size()/2) };

                for (int i = 0; i < n; i++) {
                    if (stays.get(i)) continue;                          // 숙소 제외
                    if (userRequested.contains(names.get(i))) continue;  // 사용자 요청 제외(50km 알림은 별도)
                    double d = haversine(center, coords.get(i));
                    if (d > CLUSTER_RADIUS) {
                        java.util.Map<String, String> req = new java.util.HashMap<>();
                        req.put("place", names.get(i));
                        req.put("req", "이 장소가 그 날 다른 일정들의 중심에서 "
                                + Math.round(d / 1000.0) + "km나 떨어져 동선이 흩어집니다. "
                                + "그 날 다른 장소들과 15km 이내로 모이는, 같은 권역의 가까운 다른 실존 장소로 교체해 주세요.");
                        result.add(req);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[findFarPlaces 실패] " + e.getMessage());
        }
        return result;
    }

    /** extra_notes 등에서 사용자가 직접 요청한 장소명 추출 (교체 금지 목록) */
    private java.util.Set<String> extractUserRequestedNames(PlanInputForm form) {
        java.util.Set<String> set = new java.util.HashSet<>();
        if (form == null || form.getExtraNotes() == null) return set;
        try {
            JsonNode arr = objectMapper.readTree(form.getExtraNotes());
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    // EXTRA 태그 value에 장소명이 담기는 구조를 가정 (label/value 또는 문자열)
                    String v = n.has("value") ? n.path("value").asText("") : n.asText("");
                    if (v != null && !v.isBlank()) set.add(v.trim());
                }
            }
        } catch (Exception ignore) {}
        return set;
    }
    // ─────────────────────────────────────────────────────────────
    //  ★카카오 API로 transit 거리/시간/비용을 실제값으로 덮어쓴다.
    //   AI가 지어낸 부정확한 km 대신 카카오맵 길찾기와 동일한 값을 채운다.
    //   (AI는 장소·순서만 책임지고, 숫자는 카카오가 책임 → 정확도↑·토큰↓)
    // ─────────────────────────────────────────────────────────────
    private String recalcTransitWithKakao(String json, String transportType, String destination) {
        try {
            System.out.println("🗺️ [카카오 보정 시작] 이동수단=" + transportType + " / 여행지=" + destination
                    + " / 키 길이=" + (kakaoRestKey == null ? "null" : kakaoRestKey.length()));
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) return json;

            boolean isCar = transportType == null || !transportType.contains("대중교통");

            // 같은 장소명 좌표는 캐시해 중복 호출 방지(토큰·API콜 절약)
            java.util.Map<String, double[]> geoCache = new java.util.HashMap<>();

            for (JsonNode dayNode : root) {
                JsonNode placesNode = dayNode.path("places");
                if (!placesNode.isArray()) continue;
                com.fasterxml.jackson.databind.node.ArrayNode places =
                        (com.fasterxml.jackson.databind.node.ArrayNode) placesNode;

                // 1) 모든 실제 장소에 좌표(lat/lng)를 박는다 → 프론트가 이 좌표로 마커·동선을 그린다
                for (int i = 0; i < places.size(); i++) {
                    JsonNode pl = places.get(i);
                    if (pl.has("transit") || !pl.has("name")) continue;
                    String nm = pl.path("name").asText("");
                    double[] c = geocodeCached(withRegion(destination, nm), geoCache);
                    com.fasterxml.jackson.databind.node.ObjectNode plObj =
                            (com.fasterxml.jackson.databind.node.ObjectNode) pl;
                    if (c != null) {
                        plObj.put("lat", c[0]);
                        plObj.put("lng", c[1]);
                        plObj.put("isFound", true);
                    } else {
                        plObj.put("isFound", false);
                        System.out.println("⚠️ [좌표 실패] " + nm);
                    }
                }

                // 2) transit 거리/시간/비용 계산 (위에서 박은 좌표 재사용 → 추가 API 호출 없음)
                for (int i = 0; i < places.size(); i++) {
                    if (!places.get(i).has("transit")) continue;
                    JsonNode prev = i > 0 ? places.get(i - 1) : null;
                    JsonNode next = i < places.size() - 1 ? places.get(i + 1) : null;
                    if (prev == null || next == null) continue;

                    double[] from = (prev.has("lat") && prev.has("lng"))
                            ? new double[]{ prev.path("lat").asDouble(), prev.path("lng").asDouble() } : null;
                    double[] to = (next.has("lat") && next.has("lng"))
                            ? new double[]{ next.path("lat").asDouble(), next.path("lng").asDouble() } : null;
                    if (from == null || to == null) continue;

                    long[] r = carDirections(from, to);
                    if (r == null) {
                        System.out.println("⚠️ [길찾기 실패] " + prev.path("name").asText("") + " → " + next.path("name").asText(""));
                        continue;
                    }
                    double km = r[0] / 1000.0;
                    long toll = r[2];
                    long min;
                    String cost;
                    String icon;
                    if (isCar) {
                        min = Math.round(r[1] / 60.0);
                        cost = String.format("₩%,d", Math.round(km / 10.0 * 2000.0) + toll);
                        icon = "🚗 자차";
                    } else {
                        // 대중교통은 자차 도로거리/시간을 그대로 쓰면 안 됨.
                        // 환승·대기 포함 평균 표정속도(약 22km/h)로 소요시간 보정.
                        min = Math.round(km / 22.0 * 60.0);
                        cost = "대중교통 운임 별도";
                        icon = "🚌 대중교통";
                    }
                    System.out.println("✅ [카카오] " + prev.path("name").asText("") + " → "
                            + next.path("name").asText("") + " : " + String.format("%.1f", km) + "km / " + min + "분");
                    ((com.fasterxml.jackson.databind.node.ObjectNode) places.get(i))
                            .put("transit", String.format("%s · %.1fkm · 약 %d분 · %s", icon, km, min, cost));
                }

                // ★budget 재계산: 카카오가 채운 transit 금액 + 장소 sub 금액 합으로 그날 총액 갱신
                long dayTotal = 0;
                for (JsonNode pl : places) {
                    if (pl.has("transit")) {
                        dayTotal += parseAmountFromSub(pl.path("transit").asText(""));
                    } else {
                        dayTotal += parseAmountFromSub(pl.path("sub").asText(""));
                    }
                }
                ((com.fasterxml.jackson.databind.node.ObjectNode) dayNode)
                        .put("budget", "₩" + String.format("%,d", dayTotal));
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            System.err.println("[recalcTransitWithKakao] 실패, 원본 유지: " + e.getMessage());
            return json;
        }
    }

    // 좌표 조회 결과를 호출 간 공유하는 인스턴스 캐시(중복 카카오 호출 방지)
    private final java.util.Map<String, double[]> geoSharedCache = new java.util.concurrent.ConcurrentHashMap<>();

    private double[] geocodeCached(String name, java.util.Map<String, double[]> cache) {
        if (name == null || name.isBlank()) return null;
        if (cache.containsKey(name)) return cache.get(name);
        // 공유 캐시에 있으면 카카오 재호출 없이 재사용
        double[] shared = geoSharedCache.get(name);
        if (shared != null) {
            cache.put(name, shared);
            return shared;
        }
        double[] geo = geocode(name);
        cache.put(name, geo);
        if (geo != null) geoSharedCache.put(name, geo);
        return geo;
    }


    // 장소명 앞에 여행지(시/도)를 붙여 동명 타지역 오인식을 막는다.
    private String withRegion(String destination, String placeName) {
        if (placeName == null || placeName.isBlank()) return placeName;
        if (destination == null || destination.isBlank()) return placeName;
        // 여행지 전체(시/도 + 시/군/구)를 접두어로 사용해 동명 타지역 오인식을 막는다.
        // 예) "부산 해운대구" → "부산 해운대구 부산복집" (기존엔 "부산"만 떼어 서울 결과가 잡혔음)
        String region = destination.trim();
        // 이미 장소명에 구/군 단위가 들어가 있으면 그대로 둔다(중복 방지)
        String[] tokens = region.split("\\s+");
        String lastToken = tokens[tokens.length - 1]; // 예: "해운대구"
        if (placeName.contains(lastToken)) return placeName;
        return region + " " + placeName;
    }
    // 장소명 → [위도, 경도]. 여러 변형으로 시도해 좌표 실패를 최소화한다.
    private double[] geocode(String placeName) {
        if (placeName == null || placeName.isBlank()) return null;
        String q = placeName.trim();

        double[] r = geocodeOnce(q);
        if (r != null) return r;

        String noSpace = q.replaceAll("\\s+", "");
        if (!noSpace.equals(q)) {
            r = geocodeOnce(noSpace);
            if (r != null) return r;
        }

        String[] parts = q.split("\\s+");
        for (int drop = 1; drop < parts.length; drop++) {
            StringBuilder sb = new StringBuilder();
            for (int i = drop; i < parts.length; i++) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(parts[i]);
            }
            r = geocodeOnce(sb.toString());
            if (r != null) return r;
        }
        return null;
    }

    // 단일 query로 카카오 키워드 검색. 인코딩 후 100자 초과면 호출하지 않는다(카카오가 400을 내므로).
    private double[] geocodeOnce(String query) {
        if (query == null || query.isBlank()) return null;
        try {
            // ★이중 인코딩 방지: 직접 encode 하지 않고, 인코딩된 URI 문자열로 URI 객체를 만들어
            //   RestTemplate이 재인코딩하지 않도록 한다. (기존엔 RestTemplate이 % 를 또 인코딩해
            //   카카오에 "%EC%..." 글자 그대로 검색돼 결과가 0개였음)
            String enc = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
            String urlStr = "https://dapi.kakao.com/v2/local/search/keyword.json?query=" + enc;
            java.net.URI uri = java.net.URI.create(urlStr); // 이미 인코딩된 문자열 → 재인코딩 안 함

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoRestKey);
            org.springframework.http.HttpEntity<Void> req = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<String> res = restTemplate.exchange(
                    uri, org.springframework.http.HttpMethod.GET, req, String.class);

            JsonNode docs = objectMapper.readTree(res.getBody()).path("documents");
            if (!docs.isArray() || docs.isEmpty()) return null;

            // ★ 지역 필터: query 첫 토큰(시/도)으로 address_name 검증 → 동명 타지역 오인식 차단
            // "부산 해운대구 연돈" 검색 시 카카오가 서울 결과를 1순위로 올려도 부산 결과를 찾아서 반환
            String regionKeyword = null;
            String[] qTokens = query.split("\\s+");
            if (qTokens.length >= 2) {
                regionKeyword = qTokens[0];
            }
            for (JsonNode doc : docs) {
                if (regionKeyword != null) {
                    String addr = doc.path("address_name").asText("");
                    if (!addr.contains(regionKeyword)) continue;
                }
                return new double[]{ doc.path("y").asDouble(), doc.path("x").asDouble() }; // [위도, 경도]
            }
            return null;
        } catch (org.springframework.web.client.HttpStatusCodeException he) {
            System.err.println("[geocode] HTTP " + he.getStatusCode() + " (" + query + ") : "
                    + he.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            System.err.println("[geocode] 실패(" + query + "): " + e.getMessage());
            return null;
        }
    }

    // 좌표 → [거리(m), 시간(초), 통행료(원)] (카카오 모빌리티 자차 길찾기)
    // 좌표쌍별 길찾기 결과 캐시(동일 구간 중복 카카오 호출 방지)
    private final java.util.Map<String, long[]> dirCache = new java.util.concurrent.ConcurrentHashMap<>();

    private long[] carDirections(double[] from, double[] to) {
        String key = from[0] + "," + from[1] + "_" + to[0] + "," + to[1];
        long[] cached = dirCache.get(key);
        if (cached != null) return cached;
        long[] r = carDirectionsApi(from, to);
        if (r != null) dirCache.put(key, r);
        return r;
    }

    private long[] carDirectionsApi(double[] from, double[] to) {
        try {
            String url = String.format(
                    "https://apis-navi.kakaomobility.com/v1/directions?origin=%f,%f&destination=%f,%f",
                    from[1], from[0], to[1], to[0]); // origin/destination = 경도,위도
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoRestKey);
            org.springframework.http.HttpEntity<Void> req = new org.springframework.http.HttpEntity<>(headers);
            org.springframework.http.ResponseEntity<String> res = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, req, String.class);
            JsonNode routes = objectMapper.readTree(res.getBody()).path("routes");
            if (!routes.isArray() || routes.isEmpty()) return null;
            JsonNode s = routes.get(0).path("summary");
            return new long[]{
                    s.path("distance").asLong(0),
                    s.path("duration").asLong(0),
                    s.path("fare").path("toll").asLong(0)
            };
        } catch (org.springframework.web.client.HttpStatusCodeException he) {
            System.err.println("[carDirections] HTTP " + he.getStatusCode() + " : "
                    + he.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            System.err.println("[carDirections] 실패: " + e.getMessage());
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  ★ 신규 파이프라인 프롬프트 메서드 (커밋2: 추가만 — 커밋3에서 컨트롤러 연결)
    // ════════════════════════════════════════════════════════════════

    /**
     * AI에게 type별(stay/food/cafe/tour) 장소 후보 리스트만 생성하도록 요청한다.
     * 좌표·순서·날짜·transit 은 일절 포함하지 않음.
     * 커밋3에서 geocodeAndFilterCandidates → clusterByDay → buildDaySkeleton 에 연결된다.
     *
     * @return {"stay":[...],"food":[...],"cafe":[...],"tour":[...]} 형태의 JSON 문자열
     */
    public String generateCandidates(Long tripId) {
        TravelPlan plan = planRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));
        PlanInputForm form = plan.getForm();
        if (form == null) throw new IllegalStateException("해당 플랜의 취향 정보가 DB에 없습니다.");

        boolean isDayTrip = plan.getStartDate() != null && plan.getStartDate().equals(plan.getEndDate());
        long numNights = isDayTrip ? 0
                : plan.getEndDate().toEpochDay() - plan.getStartDate().toEpochDay();
        long numDays = numNights + 1;
        int cnt = form.getCompanionCount();

        String stayInstruction = isDayTrip
                ? "\"stay\" 배열은 반드시 빈 배열([])로 출력하세요. 이 여행은 0박 당일치기입니다."
                : "\"stay\" 배열에는 숙소 유형 \"" + form.getAccommodationType()
                  + "\" 에 맞는 실존 업소명 후보 10~15개를 생성하세요. 옵션: " + form.getAccommodationOptions();

        String prompt = "당신은 'TripLinker'의 여행 장소 조사 전문 AI입니다.\n"
                + "아래 여행 조건에 맞는 실존 장소 후보 목록을 type별로 생성하세요.\n"
                + "★일정 순서·날짜·시간·이동 정보는 절대 출력하지 마세요. 후보 목록만 출력합니다.★\n\n"
                + "[여행 기본 정보]\n"
                + "- 여행지: " + plan.getDestination()
                + " / 일정: " + plan.getStartDate() + " ~ " + plan.getEndDate()
                + " (" + numNights + "박 " + numDays + "일) / 총 예산: " + form.getBudget() + "원\n"
                + "- 인원: " + form.getCompanionType() + " (" + cnt + "명)"
                + " / 유아 동반: " + (form.getHasInfant() == 1 ? "O" : "X")
                + " / 반려동물 동반: " + (form.getHasPet() == 1 ? "O" : "X") + "\n"
                + "- 이동 수단: " + form.getTransportType()
                + " / 스타일: " + form.getTravelStyles()
                + " / 식이: " + form.getDietaryInfo() + "\n"
                + "- 유저 요청 사항: " + parseExtraNotesToPrompt(form.getExtraNotes()) + "\n\n"
                + "[후보 생성 규칙]\n"
                + "1. 장소명 절대 규칙 (Hallucination 차단):\n"
                + "   - 반드시 카카오맵(KakaoMap)에 공식 등록된 실존 상호명 전체를 정확히 기입하세요.\n"
                + "   - \"OO 맛집\", \"OO 거리\", \"OO 먹자골목\" 등 포괄·집합 명칭 절대 금지.\n"
                + "   - 폐업·가상 장소 금지. 이름 뒤에 '체크인', '방문' 등 접미사 금지.\n"
                + "2. 수량: type별 10~20개씩 넉넉히 생성하세요 (서버가 선별해서 사용함).\n"
                + "3. 지역 일관성: 모든 후보는 여행지(" + plan.getDestination() + ") 경계 안에 실존해야 합니다.\n"
                + "4. sub 규격:\n"
                + "   - stay : \"숙소 · ₩금액\" (1박 기준 실제 시세, 예: ₩120,000)\n"
                + "   - food : \"맛집 · 점심 · ₩12,000×" + cnt + "\" 또는 \"맛집 · 저녁 · ₩15,000×" + cnt + "\" 형태\n"
                + "   - cafe : \"카페 · ₩8,000×" + cnt + "\" 형태\n"
                + "   - tour : \"관광지 · 1h · ₩5,000×" + cnt + "\" (무료면 ₩0×" + cnt + ")\n"
                + "5. stars: 항상 \"평점 정보 없음\" 으로만 출력하세요. 숫자를 지어내는 것 절대 금지.\n"
                + "6. " + stayInstruction + "\n"
                + "7. 유저 요청 사항에 특정 장소·식당이 명시됐다면 해당 타입 배열의 첫 번째 항목으로 반드시 포함.\n"
                + "8. 숙소 업종 일치: stay 배열에는 \"" + form.getAccommodationType() + "\" 유형만 포함하세요.\n"
                + "9. 식이 옵션(" + form.getDietaryInfo() + ")이 있으면 food 후보에 반영하세요.\n\n"
                + "[출력 형식 — 이 JSON 구조만 출력, 설명·마크다운 코드블럭 금지]\n"
                + "{\n"
                + "  \"stay\": [ {\"name\":\"실존 숙소명\", \"sub\":\"숙소 · ₩금액\", \"stars\":\"평점 정보 없음\"} ],\n"
                + "  \"food\": [ {\"name\":\"실존 식당명\", \"sub\":\"맛집 · 점심 · ₩금액×" + cnt + "\", \"stars\":\"평점 정보 없음\"} ],\n"
                + "  \"cafe\": [ {\"name\":\"실존 카페명\", \"sub\":\"카페 · ₩금액×" + cnt + "\", \"stars\":\"평점 정보 없음\"} ],\n"
                + "  \"tour\": [ {\"name\":\"실존 관광지명\", \"sub\":\"관광지 · 1h · ₩금액×" + cnt + "\", \"stars\":\"평점 정보 없음\"} ]\n"
                + "}";

        String raw = "{}";
        try {
            System.out.println("🔍 [후보 생성] Claude 기동 중 (" + plan.getDestination() + " " + numDays + "일)...");
            raw = claudeClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            System.out.println("⚠️ [후보 생성] Claude 실패, Groq 폴백: " + e.getMessage());
            try {
                raw = primaryClient.prompt().user(prompt).call().content();
            } catch (Exception ex) {
                System.err.println("🚨 [후보 생성] 모든 LLM 실패: " + ex.getMessage());
            }
        }

        // 마크다운 잔해 제거 (```json ... ``` 등)
        if (raw != null && raw.contains("{")) {
            int start = raw.indexOf("{");
            int end = raw.lastIndexOf("}");
            if (start >= 0 && end > start) raw = raw.substring(start, end + 1);
        }
        System.out.println("✅ [후보 생성 완료] " + plan.getDestination());
        return (raw != null) ? raw.trim() : "{}";
    }

    /**
     * 후보 JSON → 좌표 확보 → 클러스터링 → 시간 골격 조립.
     * saveAiRouteToDb 에 바로 넘길 수 있는 완성 일정 JSON 문자열을 반환한다.
     * (커밋3: 컨트롤러 generateRoute 에서 generateCandidates 다음에 호출)
     */
    public String assembleCandidates(Long tripId, String candidatesJson) {
        TravelPlan plan = planRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다."));
        PlanInputForm form = plan.getForm();
        java.util.Set<String> userRequested = extractUserRequestedNames(form);
        java.util.Map<String, double[]> geoCache = new java.util.HashMap<>();
        try {
            JsonNode candidatesNode = objectMapper.readTree(candidatesJson);
            java.util.Map<String, java.util.List<com.fasterxml.jackson.databind.node.ObjectNode>> filtered =
                    geocodeAndFilterCandidates(candidatesNode, plan.getDestination(), geoCache);
            java.util.Map<Integer, java.util.Map<String, java.util.List<com.fasterxml.jackson.databind.node.ObjectNode>>> clusters =
                    clusterByDay(filtered, plan, userRequested);
            return buildDaySkeleton(clusters, plan, form, userRequested);
        } catch (Exception e) {
            System.err.println("[assembleCandidates] 실패: " + e.getMessage());
            return "[]";
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  ★ 신규 파이프라인 조립 메서드 (커밋1: 추가만 — 커밋3에서 컨트롤러 연결)
    //   generateCandidates() 결과를 받아:
    //     geocodeAndFilterCandidates → clusterByDay → buildDaySkeleton
    //   순으로 호출해 기존 saveAiRouteToDb 가 바로 받을 수 있는 일정 JSON 을 만든다.
    // ════════════════════════════════════════════════════════════════

    /**
     * 후보 장소 JSON 전체를 geocoding 하고 좌표 실패·타지역 이상치를 제거한다.
     * 입력: {"stay":[...],"food":[...],"cafe":[...],"tour":[...]} (generateCandidates 출력 구조)
     * 출력: 각 항목에 "lat"/"lng" 추가, 좌표를 못 잡은 항목 제거, 타지역 이상치 제거
     *
     * §9: 여행지 중심 좌표 고정 반경 차단(권역 잠금) 재도입 금지.
     *     타지역 판단은 '후보들끼리 뭉치는 중심(중앙값)' 기준 80km 초과만.
     */
    private java.util.Map<String, java.util.List<com.fasterxml.jackson.databind.node.ObjectNode>>
            geocodeAndFilterCandidates(
                    JsonNode candidatesJson,
                    String destination,
                    java.util.Map<String, double[]> geoCache) {

        String[] types = {"stay", "food", "cafe", "tour"};
        java.util.Map<String, java.util.List<com.fasterxml.jackson.databind.node.ObjectNode>> result
                = new java.util.LinkedHashMap<>();
        java.util.List<com.fasterxml.jackson.databind.node.ObjectNode> all = new java.util.ArrayList<>();

        for (String type : types) {
            java.util.List<com.fasterxml.jackson.databind.node.ObjectNode> list = new java.util.ArrayList<>();
            result.put(type, list);
            JsonNode arr = candidatesJson.path(type);
            if (!arr.isArray()) continue;
            for (JsonNode item : arr) {
                com.fasterxml.jackson.databind.node.ObjectNode node =
                        (com.fasterxml.jackson.databind.node.ObjectNode) item.deepCopy();
                String nm = node.path("name").asText("").trim();
                if (nm.isEmpty()) continue;
                double[] c = geocodeCached(withRegion(destination, nm), geoCache);
                if (c == null) {
                    System.out.println("[후보탈락-좌표없음] " + nm);
                    continue;
                }
                node.put("lat", c[0]);
                node.put("lng", c[1]);
                list.add(node);
                all.add(node);
            }
        }

        // 전체 후보 중앙값 기준 80km 초과 → 타지역 이상치로 보고 제거
        if (all.size() >= 3) {
            java.util.List<Double> lats = new java.util.ArrayList<>();
            java.util.List<Double> lngs = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.node.ObjectNode n : all) {
                lats.add(n.path("lat").asDouble());
                lngs.add(n.path("lng").asDouble());
            }
            java.util.Collections.sort(lats);
            java.util.Collections.sort(lngs);
            double[] center = {lats.get(lats.size() / 2), lngs.get(lngs.size() / 2)};
            final double OUTLIER_DIST = 80_000.0;
            for (String type : types) {
                result.get(type).removeIf(n -> {
                    double[] c = {n.path("lat").asDouble(), n.path("lng").asDouble()};
                    double dist = haversine(center, c);
                    if (dist > OUTLIER_DIST) {
                        System.out.println("[후보탈락-타지역] " + n.path("name").asText("")
                                + " (" + Math.round(dist / 1000.0) + "km)");
                        return true;
                    }
                    return false;
                });
            }
        }
        return result;
    }

    /**
     * 좌표 확보된 후보를 일자별 클러스터에 배분한다.
     * - tour/cafe: farthest-first K-means 씨앗 선택 후 1회 배정
     * - food    : 라운드로빈으로 일자별 배정
     * - stay    : 첫 번째 숙소 후보를 Day 1 ~ N-1 에 동일하게 배정 (연박 가정)
     */
    private java.util.Map<Integer, java.util.Map<String, java.util.List<com.fasterxml.jackson.databind.node.ObjectNode>>>
            clusterByDay(
                    java.util.Map<String, java.util.List<com.fasterxml.jackson.databind.node.ObjectNode>> geocodedCandidates,
                    TravelPlan plan,
                    java.util.Set<String> userRequested) {

        int numDays = (int) Math.max(1,
                plan.getEndDate().toEpochDay() - plan.getStartDate().toEpochDay() + 1);

        java.util.Map<Integer, java.util.Map<String, java.util.List<com.fasterxml.jackson.databind.node.ObjectNode>>> result
                = new java.util.LinkedHashMap<>();
        for (int d = 1; d <= numDays; d++) {
            java.util.Map<String, java.util.List<com.fasterxml.jackson.databind.node.ObjectNode>> dm
                    = new java.util.LinkedHashMap<>();
            for (String t : new String[]{"stay", "food", "cafe", "tour"}) dm.put(t, new java.util.ArrayList<>());
            result.put(d, dm);
        }

        // tour + cafe 를 임시 _ct 마커와 함께 합쳐서 클러스터링
        java.util.List<com.fasterxml.jackson.databind.node.ObjectNode> activities = new java.util.ArrayList<>();
        for (com.fasterxml.jackson.databind.node.ObjectNode n
                : geocodedCandidates.getOrDefault("tour", java.util.Collections.emptyList())) {
            com.fasterxml.jackson.databind.node.ObjectNode copy = n.deepCopy();
            copy.put("_ct", "tour");
            activities.add(copy);
        }
        for (com.fasterxml.jackson.databind.node.ObjectNode n
                : geocodedCandidates.getOrDefault("cafe", java.util.Collections.emptyList())) {
            com.fasterxml.jackson.databind.node.ObjectNode copy = n.deepCopy();
            copy.put("_ct", "cafe");
            activities.add(copy);
        }

        // farthest-first 씨앗 선택 → 최근접 씨앗(=일자)에 배정
        if (!activities.isEmpty()) {
            java.util.List<double[]> seeds = buildClusterSeeds(activities, numDays);
            for (com.fasterxml.jackson.databind.node.ObjectNode n : activities) {
                double[] c = {n.path("lat").asDouble(), n.path("lng").asDouble()};
                int bestDay = nearestSeedDay(c, seeds);
                String ct = n.path("_ct").asText("tour");
                n.put("type", ct);   // tagPlace 에서 읽을 type 필드로 승격
                n.remove("_ct");
                result.get(bestDay).get(ct).add(n);
            }
        }

        // food: 라운드로빈
        java.util.List<com.fasterxml.jackson.databind.node.ObjectNode> foods =
                new java.util.ArrayList<>(geocodedCandidates.getOrDefault("food", java.util.Collections.emptyList()));
        for (int i = 0; i < foods.size(); i++) {
            result.get((i % numDays) + 1).get("food").add(foods.get(i));
        }

        // stay: 첫 번째 숙소를 Day 1~N-1 에 각각 복사 배정 (당일치기=N=1 이면 배정 없음)
        java.util.List<com.fasterxml.jackson.databind.node.ObjectNode> stays =
                geocodedCandidates.getOrDefault("stay", java.util.Collections.emptyList());
        if (!stays.isEmpty() && numDays > 1) {
            com.fasterxml.jackson.databind.node.ObjectNode baseStay = stays.get(0);
            for (int d = 1; d <= numDays - 1; d++) {
                result.get(d).get("stay").add(baseStay.deepCopy());
            }
        }
        return result;
    }

    /**
     * 일자별 클러스터로 §7 시간 골격에 맞춘 완성 일정 JSON 문자열을 조립한다.
     * - transit 은 {"transit":"이동"} 만 삽입 (recalcTransitWithKakao 가 숫자를 채운다)
     * - 조립 후 reorderWithinTimeBlocks 로 끼니 사이 tour/cafe 순서를 거리순 최적화
     */
    private String buildDaySkeleton(
            java.util.Map<Integer, java.util.Map<String, java.util.List<com.fasterxml.jackson.databind.node.ObjectNode>>> dayClusters,
            TravelPlan plan,
            PlanInputForm form,
            java.util.Set<String> userRequested) {

        int numDays = dayClusters.size();
        boolean isDayTrip = plan.getStartDate().equals(plan.getEndDate());
        boolean lastDayDinner = hasLastDayDinnerRequest(
                form != null ? form.getExtraNotes() : null, numDays);
        java.time.LocalDate startDate = plan.getStartDate();

        com.fasterxml.jackson.databind.node.ArrayNode root = objectMapper.createArrayNode();

        for (int day = 1; day <= numDays; day++) {
            java.util.Map<String, java.util.List<com.fasterxml.jackson.databind.node.ObjectNode>> bucket
                    = dayClusters.get(day);
            if (bucket == null) continue;

            boolean isFirst = (day == 1);
            boolean isLast  = (day == numDays);

            java.util.List<com.fasterxml.jackson.databind.node.ObjectNode> stayList =
                    bucket.getOrDefault("stay", java.util.Collections.emptyList());
            java.util.Deque<com.fasterxml.jackson.databind.node.ObjectNode> foodQ =
                    new java.util.ArrayDeque<>(bucket.getOrDefault("food", java.util.Collections.emptyList()));
            java.util.Deque<com.fasterxml.jackson.databind.node.ObjectNode> actQ = new java.util.ArrayDeque<>();
            actQ.addAll(bucket.getOrDefault("tour", java.util.Collections.emptyList()));
            actQ.addAll(bucket.getOrDefault("cafe", java.util.Collections.emptyList()));

            com.fasterxml.jackson.databind.node.ObjectNode stayNode =
                    stayList.isEmpty() ? null : stayList.get(0);
            java.util.List<com.fasterxml.jackson.databind.node.ObjectNode> slots = new java.util.ArrayList<>();
            int keySeq = 0;

            // 1) 아침: 숙소 체크아웃 (1일차·당일치기 제외)
            if (!isFirst && !isDayTrip && stayNode != null) {
                slots.add(tagPlace(stayNode, "stay", "08:00", "d" + day + "_" + (++keySeq)));
            }
            // 2) 오전 활동 1개 (1일차 제외)
            if (!isFirst && !actQ.isEmpty()) {
                slots.add(tagPlace(actQ.poll(), null, "10:00", "d" + day + "_" + (++keySeq)));
            }
            // 3) 점심
            if (!foodQ.isEmpty()) {
                slots.add(tagPlace(foodQ.poll(), "food", "12:00", "d" + day + "_" + (++keySeq)));
            }
            // 4) 오후 활동 최대 2개
            for (String t : new String[]{"14:00", "15:30"}) {
                if (!actQ.isEmpty()) {
                    slots.add(tagPlace(actQ.poll(), null, t, "d" + day + "_" + (++keySeq)));
                }
            }
            // 5) 저녁 (마지막날은 사용자 요청 시에만)
            if (!isLast || lastDayDinner) {
                if (!foodQ.isEmpty()) {
                    slots.add(tagPlace(foodQ.poll(), "food", "18:00", "d" + day + "_" + (++keySeq)));
                }
            }
            // 6) 밤: 숙소 체크인 (마지막날·당일치기 제외)
            if (!isLast && !isDayTrip && stayNode != null) {
                slots.add(tagPlace(stayNode, "stay", "20:00", "d" + day + "_" + (++keySeq)));
            }

            // transit {"transit":"이동"} 삽입
            com.fasterxml.jackson.databind.node.ArrayNode placesNode = objectMapper.createArrayNode();
            for (int i = 0; i < slots.size(); i++) {
                if (i > 0) {
                    com.fasterxml.jackson.databind.node.ObjectNode tr = objectMapper.createObjectNode();
                    tr.put("transit", "이동");
                    placesNode.add(tr);
                }
                placesNode.add(slots.get(i));
            }

            java.time.LocalDate date = startDate.plusDays(day - 1);
            String label = String.format("📅 Day %d · %02d/%02d (%s)",
                    day, date.getMonthValue(), date.getDayOfMonth(),
                    getDayOfWeekKorean(date.getDayOfWeek()));

            com.fasterxml.jackson.databind.node.ObjectNode dayNode = objectMapper.createObjectNode();
            dayNode.put("day", day);
            dayNode.put("label", label);
            dayNode.put("budget", "₩0"); // recalcTransitWithKakao 가 재계산
            dayNode.set("places", placesNode);
            root.add(dayNode);
        }

        try {
            String json = objectMapper.writeValueAsString(root);
            return reorderWithinTimeBlocks(json, plan.getDestination(), userRequested);
        } catch (Exception e) {
            System.err.println("[buildDaySkeleton] 직렬화 실패: " + e.getMessage());
            return "[]";
        }
    }

    // ── buildDaySkeleton / clusterByDay 헬퍼 ─────────────────────────

    /** 후보 ObjectNode에 type·icon·time·key·replacePh 를 추가해 일정 place 노드로 만든다. */
    private com.fasterxml.jackson.databind.node.ObjectNode tagPlace(
            com.fasterxml.jackson.databind.node.ObjectNode candidate,
            String typeOverride,
            String time,
            String key) {
        com.fasterxml.jackson.databind.node.ObjectNode n = candidate.deepCopy();
        String t = (typeOverride != null) ? typeOverride
                : n.path("type").asText(n.path("_ct").asText("tour"));
        n.put("type", t);
        n.put("icon", typeIcon(t));
        n.put("time", time);
        n.put("key", key);
        if (!n.has("replacePh")) n.put("replacePh", "다른 장소로 교체해줘");
        n.remove("_ct");
        return n;
    }

    private String typeIcon(String type) {
        return switch (type) {
            case "stay" -> "🏨";
            case "food" -> "🍽️";
            case "cafe" -> "☕";
            default     -> "📍";
        };
    }

    /** 활동 후보 목록에서 K개의 씨앗(farthest-first)을 선택한다. */
    private java.util.List<double[]> buildClusterSeeds(
            java.util.List<com.fasterxml.jackson.databind.node.ObjectNode> activities, int k) {
        java.util.List<double[]> seeds = new java.util.ArrayList<>();
        if (activities.isEmpty()) return seeds;
        seeds.add(new double[]{
                activities.get(0).path("lat").asDouble(),
                activities.get(0).path("lng").asDouble()
        });
        while (seeds.size() < k && seeds.size() < activities.size()) {
            double[] farthest = null;
            double maxMinDist = -1;
            for (com.fasterxml.jackson.databind.node.ObjectNode n : activities) {
                final double[] c = {n.path("lat").asDouble(), n.path("lng").asDouble()};
                double minDist = seeds.stream().mapToDouble(s -> haversine(s, c)).min().orElse(0);
                if (minDist > maxMinDist) { maxMinDist = minDist; farthest = c; }
            }
            if (farthest == null) break;
            seeds.add(farthest);
        }
        return seeds;
    }

    /** 좌표에서 가장 가까운 씨앗의 1-based 인덱스(=일자)를 반환한다. */
    private int nearestSeedDay(double[] coord, java.util.List<double[]> seeds) {
        int best = 1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < seeds.size(); i++) {
            double d = haversine(seeds.get(i), coord);
            if (d < bestDist) { bestDist = d; best = i + 1; }
        }
        return best;
    }

    /** DayOfWeek → 한국어 1글자 요일 */
    private String getDayOfWeekKorean(java.time.DayOfWeek dow) {
        return switch (dow) {
            case MONDAY    -> "월";
            case TUESDAY   -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY  -> "목";
            case FRIDAY    -> "금";
            case SATURDAY  -> "토";
            case SUNDAY    -> "일";
        };
    }

    /** extra_notes 에 마지막 날 저녁 사용자 요청이 있는지 확인한다. */
    private boolean hasLastDayDinnerRequest(String extraNotesJson, int numDays) {
        if (extraNotesJson == null || extraNotesJson.isBlank()) return false;
        try {
            JsonNode arr = objectMapper.readTree(extraNotesJson);
            if (!arr.isArray()) return false;
            for (JsonNode n : arr) {
                String label = n.path("label").asText("");
                String value = n.path("value").asText("");
                if (value.isBlank()) continue;
                if (label.contains("저녁")
                        && (label.contains(numDays + "일차") || label.contains("마지막"))) {
                    return true;
                }
            }
        } catch (Exception ignore) {}
        return false;
    }
}