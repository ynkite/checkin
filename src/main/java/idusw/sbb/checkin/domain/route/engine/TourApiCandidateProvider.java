package idusw.sbb.checkin.domain.route.engine;

import java.time.LocalDate;
import java.util.List;

/**
 * 자리만 만들어 둔 실제 구현. 한국관광공사 OpenAPI(국문 관광정보·기초지자체 중심 관광지·
 * 관광지별 연관 관광지) 연동이 확정되면 여기를 채운다 — {@link PlaceCandidateProvider}
 * 계약은 이미 확정돼 있으니 호출부(엔진)는 손댈 필요가 없다.
 */
public final class TourApiCandidateProvider implements PlaceCandidateProvider {

    @Override
    public List<Candidate> findCandidates(String region, LocalDate startDate, LocalDate endDate) {
        throw new UnsupportedOperationException(
                "TourApiCandidateProvider 미구현 — 한국관광공사 OpenAPI 응답 구조 확정 전까지는 StubCandidateProvider 를 쓴다");
    }
}
