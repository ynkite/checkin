package idusw.sbb.triplinker.config;

import idusw.sbb.triplinker.domain.place.repository.PlaceRepository;
import idusw.sbb.triplinker.domain.post.repository.PlaceReviewRepository;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import idusw.sbb.triplinker.domain.admin.entity.Curation;
import idusw.sbb.triplinker.domain.admin.repository.CurationRepository;
import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.triplinker.domain.post.entity.Post;
import idusw.sbb.triplinker.domain.post.repository.PostRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import idusw.sbb.triplinker.domain.place.entity.Place;
import idusw.sbb.triplinker.domain.place.entity.PlaceCategory;
import idusw.sbb.triplinker.domain.post.entity.PlaceReview;
import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final CurationRepository curationRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final PostRepository postRepository;
    private final PlaceRepository placeRepository;
    private final PlaceReviewRepository placeReviewRepository;

    @Override
    public void run(String... args) {
        if (userRepository.existsByUsername("admin")) {
            log.info("관리자 계정이 있으므로 추가하지 않겠습니다.");
            return;
        }

        User admin = userRepository.save(User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("1234"))
                .name("관리자")
                .email("admin@triplinker.com")
                .region("서울")
                .role("ADMIN")
                .status("ACTIVE")
                .lastPwChangedAt(LocalDateTime.now())
                .build());
        log.info("관리자 계정이 생성되었습니다.");

        // ── TravelPlan 4건 ──
        TravelPlan jejuPlan = travelPlanRepository.save(TravelPlan.builder()
                .user(admin).title("제주 에메랄드 해안 여름 바캉스").destination("제주")
                .startDate(LocalDate.now().plusMonths(1))
                .endDate(LocalDate.now().plusMonths(1).plusDays(3))
                .isPublic(1).status("CONFIRMED").build());

        TravelPlan busanPlan = travelPlanRepository.save(TravelPlan.builder()
                .user(admin).title("부산 해운대 서핑 & 야경 투어").destination("부산")
                .startDate(LocalDate.now().plusMonths(1))
                .endDate(LocalDate.now().plusMonths(1).plusDays(2))
                .isPublic(1).status("CONFIRMED").build());

        TravelPlan gyeongjuPlan = travelPlanRepository.save(TravelPlan.builder()
                .user(admin).title("경주 역사 문화 기행 2박3일").destination("경주")
                .startDate(LocalDate.now().plusMonths(2))
                .endDate(LocalDate.now().plusMonths(2).plusDays(2))
                .isPublic(1).status("CONFIRMED").build());

        TravelPlan gangwonPlan = travelPlanRepository.save(TravelPlan.builder()
                .user(admin).title("강원 자연 힐링 트레킹 코스").destination("강원")
                .startDate(LocalDate.now().plusMonths(2))
                .endDate(LocalDate.now().plusMonths(2).plusDays(2))
                .isPublic(1).status("CONFIRMED").build());
        log.info("관리자 추천 경로(TravelPlan) 4건이 생성되었습니다.");

        // ── Curation 4건 ──
        String jejuExtraNotes = "{\"cardColor\":\"#E0F7FA\",\"tags\":[\"초여름\",\"힐링\",\"오션뷰\"],\"adminRecommendedAccommodations\":[\"제주 오션뷰 펜션\",\"제주 애월 감성 게스트하우스\"],\"adminRecommendedRestaurants\":[\"협재 해물라면\",\"제주 흑돼지 명가\"],\"adminRecommendedAttractions\":[\"협재해변\",\"성산일출봉\",\"한림공원\"],\"adminRecommendedCafes\":[\"성산일출봉 전망카페\",\"애월 카페거리\"],\"adminRecommendedCultures\":[],\"preferences\":{\"transport\":\"🚗 자차\",\"accommodation\":\"펜션\",\"companion\":\"커플\",\"style\":[\"힐링\",\"오션뷰\"],\"diet\":[\"해산물 선호\"],\"special\":[],\"density\":\"여유롭게\",\"accOptions\":[\"오션뷰\"]},\"days\":[{\"day\":1,\"label\":\"Day 1\",\"places\":[{\"name\":\"제주 오션뷰 펜션\",\"type\":\"🏨 숙소\",\"time\":\"15:00\",\"amount\":180000},{\"name\":\"협재 해물라면\",\"type\":\"🍽️ 맛집\",\"time\":\"12:30\",\"amount\":15000},{\"name\":\"협재해변\",\"type\":\"📍 관광지\",\"time\":\"17:00\",\"amount\":0}]},{\"day\":2,\"label\":\"Day 2\",\"places\":[{\"name\":\"성산일출봉\",\"type\":\"📍 관광지\",\"time\":\"07:00\",\"amount\":5000},{\"name\":\"성산일출봉 전망카페\",\"type\":\"☕ 카페\",\"time\":\"09:00\",\"amount\":8000},{\"name\":\"제주 흑돼지 명가\",\"type\":\"🍽️ 맛집\",\"time\":\"19:00\",\"amount\":35000}]},{\"day\":3,\"label\":\"Day 3\",\"places\":[{\"name\":\"애월 카페거리\",\"type\":\"☕ 카페\",\"time\":\"10:00\",\"amount\":9000},{\"name\":\"한림공원\",\"type\":\"📍 관광지\",\"time\":\"13:00\",\"amount\":12000}]}]}";
        curationRepository.save(Curation.builder()
                .admin(admin).plan(jejuPlan)
                .title("🌊 제주 에메랄드 해안")
                .theme("여름,힐링,오션뷰")
                .destination("제주|제주시")
                .displayOrder(1).isDefault(true)
                .extraNotes(jejuExtraNotes).build());

        String busanExtraNotes = "{\"cardColor\":\"#E3F2FD\",\"tags\":[\"여름\",\"액티비티\",\"서핑\"],\"adminRecommendedAccommodations\":[\"해운대 오션뷰 호텔\",\"광안리 비치 호텔\"],\"adminRecommendedRestaurants\":[\"해운대 회센터\",\"광안리 곱창골목\"],\"adminRecommendedAttractions\":[\"해운대 해수욕장\",\"광안리 서핑 스쿨\"],\"adminRecommendedCafes\":[\"광안리 카페거리\"],\"adminRecommendedCultures\":[],\"preferences\":{\"transport\":\"🚌 대중교통\",\"accommodation\":\"호텔\",\"companion\":\"친구\",\"style\":[\"액티비티\"],\"diet\":[\"무관\"],\"special\":[],\"density\":\"빼곡하게\",\"accOptions\":[\"오션뷰\",\"수영장\"]},\"days\":[{\"day\":1,\"label\":\"Day 1\",\"places\":[{\"name\":\"해운대 오션뷰 호텔\",\"type\":\"🏨 숙소\",\"time\":\"15:00\",\"amount\":220000},{\"name\":\"해운대 해수욕장\",\"type\":\"📍 관광지\",\"time\":\"16:00\",\"amount\":0},{\"name\":\"해운대 회센터\",\"type\":\"🍽️ 맛집\",\"time\":\"19:00\",\"amount\":40000}]},{\"day\":2,\"label\":\"Day 2\",\"places\":[{\"name\":\"광안리 서핑 스쿨\",\"type\":\"📍 관광지\",\"time\":\"10:00\",\"amount\":50000},{\"name\":\"광안리 카페거리\",\"type\":\"☕ 카페\",\"time\":\"17:00\",\"amount\":7000},{\"name\":\"광안리 곱창골목\",\"type\":\"🍽️ 맛집\",\"time\":\"20:00\",\"amount\":25000}]}]}";
        curationRepository.save(Curation.builder()
                .admin(admin).plan(busanPlan)
                .title("🏄 부산 해운대 서핑 투어")
                .destination("부산|해운대구")
                .theme("여름,액티비티,서핑")
                .displayOrder(2).isDefault(true)
                .extraNotes(busanExtraNotes).build());

        String gyeongjuExtraNotes = "{\"cardColor\":\"#FFF8E1\",\"tags\":[\"문화·역사\",\"가족\",\"힐링\"],\"adminRecommendedAccommodations\":[\"경주 한옥 스테이\",\"경주 보문 리조트\"],\"adminRecommendedRestaurants\":[\"경주 교리김밥\",\"황리단길 한정식\"],\"adminRecommendedAttractions\":[\"황리단길\",\"불국사\",\"석굴암\",\"첨성대\"],\"adminRecommendedCafes\":[],\"adminRecommendedCultures\":[\"국립경주박물관\"],\"preferences\":{\"transport\":\"🚗 자차\",\"accommodation\":\"호텔\",\"companion\":\"가족\",\"style\":[\"문화·역사\",\"힐링\"],\"diet\":[\"무관\"],\"special\":[],\"density\":\"여유롭게\",\"accOptions\":[\"조식 포함\"]},\"days\":[{\"day\":1,\"label\":\"Day 1\",\"places\":[{\"name\":\"경주 한옥 스테이\",\"type\":\"🏨 숙소\",\"time\":\"15:00\",\"amount\":150000},{\"name\":\"황리단길\",\"type\":\"📍 관광지\",\"time\":\"17:00\",\"amount\":0},{\"name\":\"황리단길 한정식\",\"type\":\"🍽️ 맛집\",\"time\":\"19:00\",\"amount\":45000}]},{\"day\":2,\"label\":\"Day 2\",\"places\":[{\"name\":\"불국사\",\"type\":\"📍 관광지\",\"time\":\"09:00\",\"amount\":6000},{\"name\":\"석굴암\",\"type\":\"📍 관광지\",\"time\":\"11:00\",\"amount\":6000},{\"name\":\"경주 교리김밥\",\"type\":\"🍽️ 맛집\",\"time\":\"13:00\",\"amount\":8000}]},{\"day\":3,\"label\":\"Day 3\",\"places\":[{\"name\":\"국립경주박물관\",\"type\":\"🎭 문화\",\"time\":\"10:00\",\"amount\":0},{\"name\":\"첨성대\",\"type\":\"📍 관광지\",\"time\":\"13:00\",\"amount\":0}]}]}";
        curationRepository.save(Curation.builder()
                .admin(admin).plan(gyeongjuPlan)
                .title("🏛️ 경주 역사 문화 기행")
                .theme("문화·역사,가족,힐링")
                .destination("경북|경주시")
                .displayOrder(3).isDefault(true)
                .extraNotes(gyeongjuExtraNotes).build());

        String gangwonExtraNotes = "{\"cardColor\":\"#E8F5E9\",\"tags\":[\"자연\",\"트레킹\",\"힐링\"],\"adminRecommendedAccommodations\":[\"속초 오션뷰 펜션\",\"평창 산장 펜션\"],\"adminRecommendedRestaurants\":[\"속초 만석닭강정\",\"양양 물치항 대게\",\"춘천 닭갈비 골목\"],\"adminRecommendedAttractions\":[\"속초 해수욕장\",\"설악산 국립공원\",\"권금성 케이블카\",\"남이섬\"],\"adminRecommendedCafes\":[],\"adminRecommendedCultures\":[],\"preferences\":{\"transport\":\"🚗 자차\",\"accommodation\":\"펜션\",\"companion\":\"친구\",\"style\":[\"힐링\",\"액티비티\"],\"diet\":[\"무관\"],\"special\":[],\"density\":\"여유롭게\",\"accOptions\":[\"취사 가능\"]},\"days\":[{\"day\":1,\"label\":\"Day 1\",\"places\":[{\"name\":\"속초 오션뷰 펜션\",\"type\":\"🏨 숙소\",\"time\":\"15:00\",\"amount\":130000},{\"name\":\"속초 해수욕장\",\"type\":\"📍 관광지\",\"time\":\"16:30\",\"amount\":0},{\"name\":\"속초 만석닭강정\",\"type\":\"🍽️ 맛집\",\"time\":\"18:00\",\"amount\":20000}]},{\"day\":2,\"label\":\"Day 2\",\"places\":[{\"name\":\"설악산 국립공원\",\"type\":\"📍 관광지\",\"time\":\"08:00\",\"amount\":0},{\"name\":\"권금성 케이블카\",\"type\":\"📍 관광지\",\"time\":\"10:00\",\"amount\":14000},{\"name\":\"양양 물치항 대게\",\"type\":\"🍽️ 맛집\",\"time\":\"13:00\",\"amount\":55000}]},{\"day\":3,\"label\":\"Day 3\",\"places\":[{\"name\":\"남이섬\",\"type\":\"📍 관광지\",\"time\":\"10:00\",\"amount\":16000},{\"name\":\"춘천 닭갈비 골목\",\"type\":\"🍽️ 맛집\",\"time\":\"13:00\",\"amount\":15000}]}]}";
        curationRepository.save(Curation.builder()
                .admin(admin).plan(gangwonPlan)
                .title("🌿 강원 자연 힐링 트레킹")
                .theme("자연,트레킹,힐링")
                .destination("강원|속초시")
                .displayOrder(4).isDefault(true)
                .extraNotes(gangwonExtraNotes).build());
        log.info("관리자 큐레이션(Curation) 4건이 생성되었습니다.");

        // ── Post 4건 ──
        postRepository.save(Post.builder()
                .user(admin).plan(jejuPlan)
                .title("제주 여름 바캉스, 협재해변에서 보낸 3박4일")
                .content("에메랄드빛 협재해변과 성산일출봉을 둘러볼 수 있는 여름 휴가 후기입니다.")
                .styleTags("여름,힐링,오션뷰").isPublic(true).build());

        postRepository.save(Post.builder()
                .user(admin).plan(busanPlan)
                .title("부산 해운대 여름 휴가, 서핑 입문 후기")
                .content("해운대와 광안리를 오가며 서핑을 배우고 야경까지 즐긴 여름 휴가 코스입니다.")
                .styleTags("여름,서핑,액티비티").isPublic(true).build());

        postRepository.save(Post.builder()
                .user(admin).plan(gyeongjuPlan)
                .title("경주 2박3일, 불국사부터 황리단길까지")
                .content("천년 고도 경주에서 역사와 힐링을 동시에! 가족 여행 코스 추천합니다.")
                .styleTags("문화·역사,가족,힐링").isPublic(true).build());

        postRepository.save(Post.builder()
                .user(admin).plan(gangwonPlan)
                .title("강원 힐링 트레킹, 설악산에서 남이섬까지")
                .content("속초·양양·춘천을 잇는 강원 자연 힐링 코스입니다.")
                .styleTags("자연,트레킹,힐링").isPublic(true).build());
        log.info("게시물(Post) 4건이 생성되었습니다.");
        // ── 일반 유저 20명 ──
        String[] regions = {"서울","경기","부산","제주","강원","대구","인천","광주","대전","울산",
                "서울","경기","부산","제주","강원","대구","인천","광주","대전","울산"};
        String[] mbtis   = {"INTJ","ENFP","ISFJ","ENTP","INFJ","ESTJ","INTP","ESFP","ENTJ","ISFP",
                "INFP","ESTJ","ENFJ","ISTP","ESFJ","INTP","ENTJ","ISFJ","ENFP","INTJ"};
        String[] genders = {"M","F","M","F","M","F","F","M","F","M","M","F","M","F","F","M","F","M","M","F"};
        String[][] userData = {
                {"user01","김민준","minjun@test.com"},  {"user02","이서연","seoyeon@test.com"},
                {"user03","박지호","jiho@test.com"},    {"user04","최수아","sua@test.com"},
                {"user05","정우진","woojin@test.com"},  {"user06","강하은","haeun@test.com"},
                {"user07","윤도현","dohyun@test.com"},  {"user08","임지우","jiwoo@test.com"},
                {"user09","한승민","seungmin@test.com"},{"user10","오예린","yerin@test.com"},
                {"user11","서준혁","junhyuk@test.com"}, {"user12","권나연","nayeon@test.com"},
                {"user13","배현우","hyunwoo@test.com"}, {"user14","조아름","areum@test.com"},
                {"user15","신태양","taeyang@test.com"}, {"user16","황보미","bomi@test.com"},
                {"user17","문성진","sungjin@test.com"}, {"user18","남지수","jisu@test.com"},
                {"user19","류재원","jaewon@test.com"},  {"user20","전하린","harin@test.com"},
        };
        User[] users = new User[20];
        for (int i = 0; i < 20; i++) {
            users[i] = userRepository.save(User.builder()
                    .username(userData[i][0])
                    .passwordHash(passwordEncoder.encode("Test1234!"))
                    .name(userData[i][1])
                    .email(userData[i][2])
                    .region(regions[i])
                    .gender(genders[i])
                    .mbti(mbtis[i])
                    .role("USER").status("ACTIVE")
                    .lastPwChangedAt(LocalDateTime.now())
                    .build());
        }
        log.info("일반 유저 20명이 생성되었습니다.");

        // ── 유저 TravelPlan 20건 (유저마다 1개씩, 지역 매핑) ──
        String[][] planData = {
                // {title, destination}
                {"제주 힐링 3박4일",        "제주"},
                {"부산 해운대 서핑 여행",    "부산"},
                {"경주 역사 탐방 1박2일",    "경주"},
                {"강원 속초 힐링 여행",      "강원"},
                {"서울 도심 당일치기",       "서울"},
                {"전주 한옥 감성 여행",      "전북"},
                {"강릉 바다 커피 여행",      "강원"},
                {"여수 밤바다 2박3일",       "전남"},
                {"남해 섬 힐링 여행",        "경남"},
                {"통영 한려수도 투어",       "경남"},
                {"제주 혼행 4박5일",        "제주"},
                {"부산 맛집 투어",          "부산"},
                {"설악산 등산 1박2일",       "강원"},
                {"경주 황리단길 카페 투어",   "경주"},
                {"제주 성산 일출 여행",      "제주"},
                {"남이섬 단풍 당일치기",     "강원"},
                {"제주 애월 카페 여행",      "제주"},
                {"강릉 안목해변 커피 여행",   "강원"},
                {"제주 성산 오션뷰 여행",    "제주"},
                {"부산 광안리 야경 여행",    "부산"},
        };
        TravelPlan[] userPlans = new TravelPlan[20];
        for (int i = 0; i < 20; i++) {
            userPlans[i] = travelPlanRepository.save(TravelPlan.builder()
                    .user(users[i])
                    .title(planData[i][0])
                    .destination(planData[i][1])
                    .startDate(LocalDate.now().minusDays(30 - i))
                    .endDate(LocalDate.now().minusDays(28 - i))
                    .isPublic(1).status("CONFIRMED").build());
        }
        log.info("유저 TravelPlan 20건이 생성되었습니다.");

        // ── Post 20건 (카테고리별 4건씩) ──
        // ROUTE 4건
        Post postRoute1 = postRepository.save(Post.builder().user(users[0]).plan(userPlans[0])
                .title("제주 3박4일 완벽 코스, 협재부터 성산까지")
                .content("제주 여행의 정석 코스를 소개합니다. 1일차는 협재해변에서 에메랄드빛 바다를 감상하고 오설록 티뮤지엄을 들렀어요. 2일차에는 성산일출봉 일출을 보고 섭지코지 산책, 저녁엔 제주시 흑돼지 거리에서 식사했습니다. 렌터카 이동이라 편했고 도로도 잘 되어있어요.")
                .styleTags("여름,힐링,오션뷰").category("ROUTE").isPublic(true).build());

        Post postRoute2 = postRepository.save(Post.builder().user(users[2]).plan(userPlans[2])
                .title("경주 1박2일, 불국사 야경이 압도적이었어요")
                .content("경주는 밤이 더 아름다웠어요. 낮에 불국사와 석굴암을 보고 저녁엔 황리단길에서 카페와 식당을 즐겼습니다. 다음날 아침 국립경주박물관 관람 후 귀가했는데 짧은 일정이었지만 알찼어요. 가족 여행으로 강추합니다.")
                .styleTags("문화·역사,가족,힐링").category("ROUTE").isPublic(true).build());

        Post postRoute3 = postRepository.save(Post.builder().user(users[4]).plan(userPlans[4])
                .title("서울 당일치기 도심 투어 — 경복궁부터 북촌까지")
                .content("서울 살면서도 제대로 투어한 적이 없었는데 이번에 제대로 다녀봤어요. 경복궁 → 북촌한옥마을 → 인사동 → 광장시장 순서로 걸어다니며 서울의 역사를 느꼈습니다. 광장시장 육회비빔밥은 정말 최고였고 북촌 한옥마을에서 사진도 많이 찍었어요.")
                .styleTags("문화·역사,도심,힐링").category("ROUTE").isPublic(true).build());

        Post postRoute4 = postRepository.save(Post.builder().user(users[8]).plan(userPlans[8])
                .title("남해 2박3일, 독일마을과 다랭이마을 완전 정복")
                .content("남해는 기대 이상이었어요. 독일마을에서 독특한 유럽 감성을 느끼고 다랭이마을 계단식 논을 보며 힐링했습니다. 바다 뷰 펜션에서 보낸 밤도 정말 좋았어요. 남해 멸치쌈밥도 꼭 드세요.")
                .styleTags("자연,액티비티,힐링").category("ROUTE").isPublic(true).build());

        // STAY 4건
        Post postStay1 = postRepository.save(Post.builder().user(users[1]).plan(userPlans[1])
                .title("해운대 오션뷰 호텔, 뷰가 진짜 미쳤어요")
                .content("해운대 바로 앞 오션뷰 룸을 예약했는데 창문 열면 바다가 눈앞에 펼쳐져요. 아침에 일어나서 바다 보면서 커피 한 잔 하는 게 꿈같은 시간이었어요. 조식도 뷔페로 운영되는데 종류가 다양하고 맛있었어요.")
                .styleTags("여름,오션뷰,럭셔리").category("STAY").isPublic(true).build());

        Post postStay2 = postRepository.save(Post.builder().user(users[3]).plan(userPlans[3])
                .title("속초 오션뷰 펜션 — 조용하고 아늑한 힐링 숙소")
                .content("속초 해변 근처 작은 펜션인데 정말 마음에 들었어요. 사장님이 직접 만들어주신 조식이 너무 맛있었고 바베큐 시설도 잘 되어있었어요. 방에서 바다 소리가 들려서 잠들기도 좋았어요.")
                .styleTags("자연,힐링,오션뷰").category("STAY").isPublic(true).build());

        Post postStay3 = postRepository.save(Post.builder().user(users[5]).plan(userPlans[5])
                .title("전주 한옥 스테이 후기 — 진짜 한국 전통을 느꼈어요")
                .content("전주 한옥마을 안에 있는 한옥 숙소를 예약했어요. 온돌방에서 자는 느낌이 처음엔 낯설었지만 아침에 일어나니 허리가 오히려 더 개운했어요. 외국인 친구랑 같이 오고 싶은 곳이에요.")
                .styleTags("문화·역사,가성비,힐링").category("STAY").isPublic(true).build());

        Post postStay4 = postRepository.save(Post.builder().user(users[10]).plan(userPlans[10])
                .title("제주 애월 감성 게스트하우스 — 혼행자에게 딱이에요")
                .content("혼자 제주 여행 가서 게스트하우스에 묵었는데 여기서 만난 여행자들이랑 같이 밥도 먹고 드라이브도 했어요. 호스트가 제주 로컬 맛집을 직접 추천해줘서 관광객이 잘 모르는 곳을 많이 다녔어요.")
                .styleTags("혼행,가성비,힐링").category("STAY").isPublic(true).build());

        // FOOD 4건
        Post postFood1 = postRepository.save(Post.builder().user(users[6]).plan(userPlans[6])
                .title("강릉 맛집 총정리 — 현지인이 알려준 진짜 맛집")
                .content("강릉 2박3일 동안 먹은 것들 정리해요. 아침은 주문진 수산시장 회덮밥, 점심은 안목해변 근처 돌솥밥 정식, 저녁은 중앙시장 닭강정이 최고였어요. 테라로사 강릉 본점에서 커피도 꼭 드세요.")
                .styleTags("음식 탐방,가성비,힐링").category("FOOD").isPublic(true).build());

        Post postFood2 = postRepository.save(Post.builder().user(users[7]).plan(userPlans[7])
                .title("여수 밤바다 맛집 — 돌게장이 인생 음식이었어요")
                .content("여수 돌게장 먹으러 일부러 여수 갔다고 해도 과언이 아니에요. 국물에 밥 비벼 먹으면 진짜 행복해요. 저녁엔 이순신광장 근처 해산물 포장마차에서 굴구이도 먹었는데 이것도 대박이었어요.")
                .styleTags("음식 탐방,해산물,여름").category("FOOD").isPublic(true).build());

        Post postFood3 = postRepository.save(Post.builder().user(users[11]).plan(userPlans[11])
                .title("부산 3대 맛집 투어 — 밀면, 돼지국밥, 씨앗호떡")
                .content("부산 여행 오면 꼭 먹어야 할 것들이에요. 개금밀면 본점에서 비빔밀면, 송정3대국밥에서 돼지국밥, 국제시장 씨앗호떡까지 하루에 다 먹었어요. 자갈치시장에서 회도 한 접시 먹었는데 신선도가 달랐어요.")
                .styleTags("음식 탐방,가성비,액티비티").category("FOOD").isPublic(true).build());

        Post postFood4 = postRepository.save(Post.builder().user(users[13]).plan(userPlans[13])
                .title("경주 황리단길 카페&맛집 탐방 후기")
                .content("황리단길은 카페와 맛집이 즐비한 경주의 핫플이에요. 황남빵 카페에서 황남빵과 팥빙수를 먹었는데 달달하고 맛있어요. 경주 교리김밥은 줄이 엄청 길지만 기다릴 가치가 있어요.")
                .styleTags("문화·역사,음식 탐방,힐링").category("FOOD").isPublic(true).build());

        // TOUR 4건
        Post postTour1 = postRepository.save(Post.builder().user(users[9]).plan(userPlans[9])
                .title("통영 한려수도 — 케이블카에서 본 남해 절경")
                .content("통영 미륵산 케이블카 타고 정상에서 한려수도를 내려다봤는데 숨이 막혔어요. 섬들이 점점이 박혀있는 풍경이 너무 아름다워요. 통영 루지도 타봤는데 어른이지만 너무 재밌었어요.")
                .styleTags("자연,액티비티,힐링").category("TOUR").isPublic(true).build());

        Post postTour2 = postRepository.save(Post.builder().user(users[12]).plan(userPlans[12])
                .title("설악산 국립공원 당일 등산 — 권금성 코스 추천")
                .content("설악산 권금성 케이블카 타고 올라가서 주변 산책로 걸었어요. 케이블카 안에서 보이는 울산바위가 진짜 장관이에요. 내려와서 비룡폭포까지 걸어갔는데 왕복 2시간 정도 걸렸어요.")
                .styleTags("자연,트레킹,힐링").category("TOUR").isPublic(true).build());

        Post postTour3 = postRepository.save(Post.builder().user(users[14]).plan(userPlans[14])
                .title("성산일출봉 일출 후기 — 새벽 5시에 올라간 보람")
                .content("새벽 4시 반에 일어나서 5시에 입장했어요. 올라가는 길이 생각보다 가파르지 않아서 30분이면 정상이에요. 일출이 시작되는 순간 정말 눈물이 날 것 같았어요.")
                .styleTags("자연,힐링,오션뷰").category("TOUR").isPublic(true).build());

        Post postTour4 = postRepository.save(Post.builder().user(users[15]).plan(userPlans[15])
                .title("남이섬 사계절 중 가을이 최고 — 단풍 명소 추천")
                .content("남이섬은 봄에도 예쁘지만 가을 단풍이 진짜 예술이에요. 메타세쿼이아길이 빨갛고 노랗게 물들면 사진이 저절로 찍혀요. 자전거 대여해서 섬 한 바퀴 도는 것도 추천해요.")
                .styleTags("자연,힐링,가성비").category("TOUR").isPublic(true).build());

        // CAFE 4건
        Post postCafe1 = postRepository.save(Post.builder().user(users[16]).plan(userPlans[16])
                .title("제주 애월 카페 투어 — 오션뷰 카페 5곳 완전 정복")
                .content("제주 애월에는 오션뷰 카페가 정말 많아요. 카페더하기는 통유리로 바다가 보이고 감귤 라테가 맛있어요. 인스타에서 유명한 애월 칸타빌레는 루프탑에서 뷰가 최고예요.")
                .styleTags("힐링,오션뷰,음식 탐방").category("CAFE").isPublic(true).build());

        Post postCafe2 = postRepository.save(Post.builder().user(users[17]).plan(userPlans[17])
                .title("강릉 테라로사 본점 — 커피 성지 순례 완료")
                .content("강릉 커피 거리에서 가장 유명한 테라로사 본점에 다녀왔어요. 넓은 공장형 인테리어가 인상적이고 커피 향이 정말 좋아요. 싱글오리진 드립커피를 마셨는데 산미가 적당하고 향이 풍부했어요.")
                .styleTags("힐링,음식 탐방,가성비").category("CAFE").isPublic(true).build());

        Post postCafe3 = postRepository.save(Post.builder().user(users[18]).plan(userPlans[18])
                .title("성산일출봉 전망 카페 — 카페에서 일출봉이 보여요")
                .content("성산일출봉 입구 근처 전망 카페에 갔는데 창밖으로 일출봉이 딱 보여요. 아메리카노 한 잔 하면서 일출봉 바라보는 시간이 너무 좋았어요. 제주 말차 라테도 유명한데 색감이 예쁘고 맛도 좋아요.")
                .styleTags("힐링,오션뷰,여름").category("CAFE").isPublic(true).build());

        Post postCafe4 = postRepository.save(Post.builder().user(users[19]).plan(userPlans[19])
                .title("광안리 카페거리 — 야경 보면서 디저트 먹는 꿈의 코스")
                .content("부산 광안리 카페거리는 밤에 가야 제맛이에요. 광안대교 야경이 빛나는 시간에 오션뷰 카페에 앉아서 디저트 먹으면 진짜 행복해요. 부산 특유의 감성 카페들이 많아서 인스타 사진 찍기도 좋아요.")
                .styleTags("여름,액티비티,오션뷰").category("CAFE").isPublic(true).build());

        log.info("유저 Post 20건이 생성되었습니다.");

        // ── Place 26건 생성 (PlaceReview에서 참조하기 위해 먼저 생성) ──
        // 숙소(ACCOMMODATION) 6건
        Place placeJejuPension = placeRepository.save(Place.builder()
                .name("제주 오션뷰 펜션").category(PlaceCategory.ACCOMMODATION)
                .address("제주특별자치도 제주시 한림읍 협재리").avgPrice(180000)
                .externalRating(new BigDecimal("4.8")).savedCount(312).build());

        Place placeHaeundaeHotel = placeRepository.save(Place.builder()
                .name("해운대 오션뷰 호텔").category(PlaceCategory.ACCOMMODATION)
                .address("부산광역시 해운대구 해운대해변로").avgPrice(220000)
                .externalRating(new BigDecimal("4.6")).savedCount(458).build());

        Place placeSokchoJejuPension = placeRepository.save(Place.builder()
                .name("속초 오션뷰 펜션").category(PlaceCategory.ACCOMMODATION)
                .address("강원도 속초시 해안가").avgPrice(130000)
                .externalRating(new BigDecimal("4.7")).savedCount(198).build());

        Place placeGyeongjuHanok = placeRepository.save(Place.builder()
                .name("경주 한옥 스테이").category(PlaceCategory.ACCOMMODATION)
                .address("경상북도 경주시 황리단길 인근").avgPrice(150000)
                .externalRating(new BigDecimal("4.7")).savedCount(221).build());

        Place placeJeonjuHanok = placeRepository.save(Place.builder()
                .name("전주 한옥 스테이").category(PlaceCategory.ACCOMMODATION)
                .address("전라북도 전주시 한옥마을").avgPrice(120000)
                .externalRating(new BigDecimal("4.6")).savedCount(176).build());

        Place placeAewolGuesthouse = placeRepository.save(Place.builder()
                .name("제주 애월 감성 게스트하우스").category(PlaceCategory.ACCOMMODATION)
                .address("제주특별자치도 제주시 애월읍").avgPrice(60000)
                .externalRating(new BigDecimal("4.5")).savedCount(143).build());

        // 맛집(RESTAURANT) 8건
        Place placeHaemulRamen = placeRepository.save(Place.builder()
                .name("협재 해물라면").category(PlaceCategory.RESTAURANT)
                .address("제주특별자치도 제주시 한림읍 협재리").avgPrice(15000)
                .externalRating(new BigDecimal("4.6")).savedCount(289).build());

        Place placeBlackPig = placeRepository.save(Place.builder()
                .name("제주 흑돼지 명가").category(PlaceCategory.RESTAURANT)
                .address("제주특별자치도 제주시").avgPrice(35000)
                .externalRating(new BigDecimal("4.7")).savedCount(334).build());

        Place placeHaeundaeHoe = placeRepository.save(Place.builder()
                .name("해운대 회센터").category(PlaceCategory.RESTAURANT)
                .address("부산광역시 해운대구").avgPrice(40000)
                .externalRating(new BigDecimal("4.5")).savedCount(256).build());

        Place placeGwangalliGopchang = placeRepository.save(Place.builder()
                .name("광안리 곱창골목").category(PlaceCategory.RESTAURANT)
                .address("부산광역시 수영구 광안리").avgPrice(25000)
                .externalRating(new BigDecimal("4.6")).savedCount(198).build());

        Place placeGyeongjuKimbap = placeRepository.save(Place.builder()
                .name("경주 교리김밥").category(PlaceCategory.RESTAURANT)
                .address("경상북도 경주시 교동").avgPrice(8000)
                .externalRating(new BigDecimal("4.8")).savedCount(412).build());

        Place placeYeosuDolgejang = placeRepository.save(Place.builder()
                .name("여수 돌게장 거리").category(PlaceCategory.RESTAURANT)
                .address("전라남도 여수시").avgPrice(30000)
                .externalRating(new BigDecimal("4.7")).savedCount(167).build());

        Place placeSokchoChicken = placeRepository.save(Place.builder()
                .name("속초 만석닭강정").category(PlaceCategory.RESTAURANT)
                .address("강원도 속초시 중앙시장").avgPrice(20000)
                .externalRating(new BigDecimal("4.6")).savedCount(245).build());

        Place placeGangneungDakgalbi = placeRepository.save(Place.builder()
                .name("춘천 닭갈비 골목").category(PlaceCategory.RESTAURANT)
                .address("강원도 춘천시").avgPrice(15000)
                .externalRating(new BigDecimal("4.5")).savedCount(189).build());

        // 카페(CAFE) 4건
        Place placeSangsanCafe = placeRepository.save(Place.builder()
                .name("성산일출봉 전망카페").category(PlaceCategory.CAFE)
                .address("제주특별자치도 서귀포시 성산읍").avgPrice(8000)
                .externalRating(new BigDecimal("4.6")).savedCount(221).build());

        Place placeAewolCafe = placeRepository.save(Place.builder()
                .name("애월 카페거리").category(PlaceCategory.CAFE)
                .address("제주특별자치도 제주시 애월읍").avgPrice(9000)
                .externalRating(new BigDecimal("4.7")).savedCount(298).build());

        Place placeTerraRosa = placeRepository.save(Place.builder()
                .name("테라로사 강릉본점").category(PlaceCategory.CAFE)
                .address("강원도 강릉시").avgPrice(7000)
                .externalRating(new BigDecimal("4.9")).savedCount(521).build());

        Place placeGwangalliCafe = placeRepository.save(Place.builder()
                .name("광안리 카페거리").category(PlaceCategory.CAFE)
                .address("부산광역시 수영구 광안리").avgPrice(9000)
                .externalRating(new BigDecimal("4.6")).savedCount(267).build());

        // 관광지(ATTRACTION) 8건
        Place placeHyeopjae = placeRepository.save(Place.builder()
                .name("협재해변").category(PlaceCategory.ATTRACTION)
                .address("제주특별자치도 제주시 한림읍").avgPrice(0)
                .externalRating(new BigDecimal("4.9")).savedCount(612).build());

        Place placeSangsanIlchulbong = placeRepository.save(Place.builder()
                .name("성산일출봉").category(PlaceCategory.ATTRACTION)
                .address("제주특별자치도 서귀포시 성산읍").avgPrice(5000)
                .externalRating(new BigDecimal("4.9")).savedCount(745).build());

        Place placeHallimPark = placeRepository.save(Place.builder()
                .name("한림공원").category(PlaceCategory.ATTRACTION)
                .address("제주특별자치도 제주시 한림읍").avgPrice(12000)
                .externalRating(new BigDecimal("4.5")).savedCount(234).build());

        Place placeBulguksa = placeRepository.save(Place.builder()
                .name("불국사").category(PlaceCategory.ATTRACTION)
                .address("경상북도 경주시").avgPrice(6000)
                .externalRating(new BigDecimal("4.8")).savedCount(389).build());

        Place placeSeokguram = placeRepository.save(Place.builder()
                .name("석굴암").category(PlaceCategory.ATTRACTION)
                .address("경상북도 경주시").avgPrice(6000)
                .externalRating(new BigDecimal("4.7")).savedCount(312).build());

        Place placeGyeongjuMuseum = placeRepository.save(Place.builder()
                .name("국립경주박물관").category(PlaceCategory.ATTRACTION)
                .address("경상북도 경주시").avgPrice(0)
                .externalRating(new BigDecimal("4.6")).savedCount(198).build());

        Place placeSeoraksan = placeRepository.save(Place.builder()
                .name("설악산 국립공원").category(PlaceCategory.ATTRACTION)
                .address("강원도 속초시").avgPrice(0)
                .externalRating(new BigDecimal("4.8")).savedCount(423).build());

        Place placeNamiIsland = placeRepository.save(Place.builder()
                .name("남이섬").category(PlaceCategory.ATTRACTION)
                .address("강원도 춘천시").avgPrice(16000)
                .externalRating(new BigDecimal("4.7")).savedCount(356).build());

        log.info("Place 26건이 생성되었습니다.");

        // ── PlaceReview 생성 (모두 ROUTE 여행경로 Post에 연결) ──
        // postRoute1: 제주 3박4일 (users[0], 제주)
        // postRoute2: 경주 1박2일 (users[2], 경주)
        // postRoute3: 서울 당일치기 (users[4], 서울)
        // postRoute4: 남해 2박3일 (users[8], 남해)

        // 숙소 후기 → 여행경로 Post에 연결
        placeReviewRepository.save(PlaceReview.builder().place(placeJejuPension).post(postRoute1).user(users[0]).rating(5).comment("협재해변 도보 3분, 오션뷰 진짜 최고예요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeJejuPension).post(postRoute1).user(users[16]).rating(4).comment("커플 여행으로 완벽한 숙소예요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeJejuPension).post(postRoute1).user(users[18]).rating(5).comment("조용하고 뷰가 너무 좋아요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeHaeundaeHotel).post(postRoute4).user(users[1]).rating(5).comment("해운대 바로 앞, 오션뷰 최고예요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeHaeundaeHotel).post(postRoute4).user(users[3]).rating(4).comment("위치 최고, 조식도 맛있어요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeHaeundaeHotel).post(postRoute4).user(users[11]).rating(4).comment("깔끔하고 서비스가 좋았어요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeSokchoJejuPension).post(postRoute4).user(users[3]).rating(5).comment("바다 소리 들으며 잠드는 게 최고예요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeSokchoJejuPension).post(postRoute4).user(users[12]).rating(4).comment("설악산 가기 전날 묵었는데 최고였어요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeGyeongjuHanok).post(postRoute2).user(users[2]).rating(5).comment("황리단길 인근 한옥 숙소, 분위기 최고").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeGyeongjuHanok).post(postRoute2).user(users[13]).rating(4).comment("전통 한옥 체험, 정말 좋았어요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeJeonjuHanok).post(postRoute3).user(users[5]).rating(5).comment("온돌방 정말 좋아요. 전통 분위기 최고").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeJeonjuHanok).post(postRoute3).user(users[7]).rating(4).comment("새벽에 한옥마을 혼자 걷는 게 꿈같았어요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeAewolGuesthouse).post(postRoute1).user(users[10]).rating(5).comment("혼행자에게 강추! 사람들이랑 친해지기 좋아요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeAewolGuesthouse).post(postRoute1).user(users[16]).rating(4).comment("호스트가 친절하고 로컬 맛집 추천 최고").build());

        // 맛집 후기 → 여행경로 Post에 연결
        placeReviewRepository.save(PlaceReview.builder().place(placeHaemulRamen).post(postRoute1).user(users[0]).rating(5).comment("협재해변 가기 전 먹은 해물라면, 국물이 진짜 진해요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeHaemulRamen).post(postRoute1).user(users[6]).rating(4).comment("신선한 해산물 가득! 제주 와서 꼭 먹어야 해요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeHaemulRamen).post(postRoute1).user(users[10]).rating(5).comment("혼자 먹기엔 양이 많지만 맛은 최고예요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeBlackPig).post(postRoute1).user(users[0]).rating(5).comment("제주 흑돼지는 육지 돼지고기랑 달라요. 꼭 드세요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeBlackPig).post(postRoute1).user(users[16]).rating(4).comment("가격이 좀 있지만 퀄리티가 달라요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeHaeundaeHoe).post(postRoute4).user(users[1]).rating(5).comment("신선도가 달라요. 부산 회는 여기서").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeHaeundaeHoe).post(postRoute4).user(users[11]).rating(4).comment("자갈치보다 이쪽이 더 좋았어요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeHaeundaeHoe).post(postRoute4).user(users[2]).rating(4).comment("양도 많고 신선해요. 가성비 좋아요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeGwangalliGopchang).post(postRoute4).user(users[11]).rating(5).comment("부산 야식의 정석이에요. 소주 한 잔이랑 완벽").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeGwangalliGopchang).post(postRoute4).user(users[19]).rating(4).comment("야경 보면서 먹는 곱창 진짜 맛있어요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeGyeongjuKimbap).post(postRoute2).user(users[2]).rating(5).comment("줄 서서 먹을 가치 100%예요. 경주 가면 필수").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeGyeongjuKimbap).post(postRoute2).user(users[13]).rating(5).comment("간이 딱 맞고 재료가 신선해요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeGyeongjuKimbap).post(postRoute2).user(users[9]).rating(4).comment("가성비 최고의 경주 대표 맛집").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeYeosuDolgejang).post(postRoute4).user(users[7]).rating(5).comment("돌게장 국물에 밥 비벼 먹으면 천국이에요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeYeosuDolgejang).post(postRoute4).user(users[8]).rating(4).comment("갓김치랑 같이 먹으면 더 맛있어요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeSokchoChicken).post(postRoute4).user(users[12]).rating(4).comment("속초 오면 무조건 닭강정! 포장해서 해변에서 먹어요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeSokchoChicken).post(postRoute4).user(users[3]).rating(5).comment("달콤하고 바삭해요. 속초 대표 간식").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeGangneungDakgalbi).post(postRoute4).user(users[15]).rating(4).comment("남이섬 다녀오다 춘천 닭갈비 필수코스").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeGangneungDakgalbi).post(postRoute3).user(users[6]).rating(5).comment("치즈 추가 강추! 볶음밥도 맛있어요").build());

        // 카페 후기 → 여행경로 Post에 연결
        placeReviewRepository.save(PlaceReview.builder().place(placeSangsanCafe).post(postRoute1).user(users[18]).rating(5).comment("창문으로 일출봉 보이는 뷰 진짜 최고예요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeSangsanCafe).post(postRoute1).user(users[14]).rating(5).comment("일출봉 올라갔다 내려와서 마신 커피 최고").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeSangsanCafe).post(postRoute1).user(users[0]).rating(4).comment("말차라테 색감도 예쁘고 맛도 좋아요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeAewolCafe).post(postRoute1).user(users[16]).rating(5).comment("애월 오션뷰 카페 중 여기가 최고예요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeAewolCafe).post(postRoute1).user(users[10]).rating(4).comment("감귤라테 너무 맛있어요. 뷰도 예뻐요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeTerraRosa).post(postRoute4).user(users[17]).rating(5).comment("커피 성지 순례 완료. 드립커피 강추").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeTerraRosa).post(postRoute4).user(users[6]).rating(5).comment("강릉 오면 무조건 테라로사! 분위기도 최고").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeTerraRosa).post(postRoute3).user(users[4]).rating(4).comment("굿즈도 예쁘고 커피도 맛있어요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeGwangalliCafe).post(postRoute4).user(users[19]).rating(5).comment("광안대교 야경 보면서 디저트 먹는 게 꿈같아요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeGwangalliCafe).post(postRoute4).user(users[11]).rating(4).comment("분위기 최고, 인스타 사진 찍기 좋아요").build());

        // 관광지 후기 → 여행경로 Post에 연결
        placeReviewRepository.save(PlaceReview.builder().place(placeHyeopjae).post(postRoute1).user(users[0]).rating(5).comment("에메랄드빛 바다가 제주에서 제일 예뻐요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeHyeopjae).post(postRoute1).user(users[14]).rating(5).comment("물이 너무 맑아요. 스노클링하기 딱 좋아요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeHyeopjae).post(postRoute1).user(users[16]).rating(4).comment("해질녘에 오면 노을이 정말 예뻐요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeSangsanIlchulbong).post(postRoute1).user(users[14]).rating(5).comment("새벽 일출은 평생 기억에 남을 것 같아요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeSangsanIlchulbong).post(postRoute1).user(users[0]).rating(5).comment("제주 여행의 하이라이트! 꼭 올라가보세요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeSangsanIlchulbong).post(postRoute1).user(users[18]).rating(4).comment("올라가는 길이 생각보다 쉬워요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeHallimPark).post(postRoute1).user(users[0]).rating(4).comment("한림공원 용암동굴이 신기해요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeHallimPark).post(postRoute1).user(users[10]).rating(5).comment("아이들이랑 오기 좋아요. 볼거리 많아요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeBulguksa).post(postRoute2).user(users[2]).rating(5).comment("야경이 너무 아름다워요. 저녁에 꼭 오세요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeBulguksa).post(postRoute2).user(users[13]).rating(4).comment("역사 공부도 되고 경치도 좋아요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeBulguksa).post(postRoute2).user(users[9]).rating(5).comment("한국 불교 건축의 정수를 볼 수 있어요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeSeokguram).post(postRoute2).user(users[2]).rating(5).comment("석굴암 본존불 정말 웅장해요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeSeokguram).post(postRoute2).user(users[13]).rating(4).comment("새벽에 오면 안개 낀 모습이 신비로워요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeGyeongjuMuseum).post(postRoute2).user(users[2]).rating(4).comment("무료인데 퀄리티가 너무 좋아요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeGyeongjuMuseum).post(postRoute2).user(users[13]).rating(5).comment("신라 유물들이 정말 많아요. 반나절은 필요해요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeSeoraksan).post(postRoute4).user(users[12]).rating(5).comment("케이블카에서 보이는 울산바위가 압도적이에요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeSeoraksan).post(postRoute4).user(users[8]).rating(4).comment("비룡폭포까지 트레킹 강추, 공기가 달라요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeSeoraksan).post(postRoute4).user(users[3]).rating(5).comment("설악산 단풍 시즌에 오면 진짜 예뻐요").build());

        placeReviewRepository.save(PlaceReview.builder().place(placeNamiIsland).post(postRoute4).user(users[15]).rating(5).comment("가을 단풍 때 오면 진짜 예술이에요").build());
        placeReviewRepository.save(PlaceReview.builder().place(placeNamiIsland).post(postRoute4).user(users[9]).rating(4).comment("자전거 타고 섬 한 바퀴 도는 게 최고예요").build());

        log.info("Place 및 PlaceReview 데이터가 생성되었습니다.");
    }

}