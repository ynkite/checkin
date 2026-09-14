package idusw.sbb.checkin.domain.plan.support;

import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.user.entity.User;

// 경로 스크랩 = 남의 여행 경로를 저장 시점 그대로 복사(스냅샷).
// 참조가 아니라 값 복사라, 원본이 수정·삭제돼도 내 스크랩본은 그대로 남는다.
public final class PlanScrapCopier {

    public static final String STATUS = "SCRAPPED";

    private PlanScrapCopier() {}

    // 원본 플랜을 스크랩한 사용자 소유의 독립 스냅샷으로 복사한다.
    public static TravelPlan copy(TravelPlan original, User scrapper) {
        return TravelPlan.builder()
                .user(scrapper)
                .title(original.getTitle())
                .destination(original.getDestination())
                .startDate(original.getStartDate())
                .endDate(original.getEndDate())
                .routeJson(original.getRouteJson())   // 일정 통째 복사 = 스냅샷
                .isPublic(0)                           // 내 스크랩본은 비공개
                .status(STATUS)                        // 내 여행 목록(FIXED)과 섞이지 않음
                .scrapedFromPlanId(original.getId())   // 원본 추적용
                .build();
    }

    // 자체 검증 — 스냅샷 독립성이 깨지면 실패
    public static void main(String[] args) {
        TravelPlan original = TravelPlan.builder()
                .id(5L).title("부산 2박3일").destination("부산")
                .routeJson("[{\"day\":1}]").isPublic(1).status("FIXED")
                .build();
        User me = User.builder().name("나").build();

        TravelPlan copy = copy(original, me);
        assert copy != original : "새 인스턴스여야 한다";
        assert "SCRAPPED".equals(copy.getStatus()) : "상태 SCRAPPED";
        assert copy.getScrapedFromPlanId() == 5L : "원본 id 기록";
        assert copy.getRouteJson().equals(original.getRouteJson()) : "일정 값 복사";
        assert copy.getIsPublic() == 0 : "스크랩본은 비공개";
        assert copy.getUser() == me : "스크랩한 사람 소유";
        assert copy.getTitle().equals("부산 2박3일") : "제목 복사";
        System.out.println("OK 스냅샷 복사 정상");
    }
}
