package idusw.sbb.checkin.domain.budget.dto;

import java.util.List;

/**
 * 축제 조회 결과 — 「확인 안 됨」과 「없음」을 구분한다.
 *
 * 관광공사 searchFestival2 는 실패해도 0건과 똑같이 빈 목록으로 보이기 쉽다.
 * 그러면 「축제가 없다」와 「확인하지 못했다」가 섞인다. 화면에서 이 둘은 다르게 적어야 한다
 * (없는 데이터를 있는 것처럼 보이지 않게).
 */
public record FestivalLookup(Status status, List<Festival> festivals) {

    public enum Status {
        FOUND,      // 확인됨 — 여행 기간에 겹치는 축제 있음
        NONE,       // 확인됨 — 없음 (API 응답 정상, 겹치는 축제 0건)
        UNKNOWN     // 확인 안 됨 — API 호출 실패 (평상시 단가로 계산했음을 화면에 밝혀야 함)
    }

    public static FestivalLookup found(List<Festival> list) { return new FestivalLookup(Status.FOUND, list); }
    public static FestivalLookup none()                     { return new FestivalLookup(Status.NONE, List.of()); }
    public static FestivalLookup unknown()                  { return new FestivalLookup(Status.UNKNOWN, List.of()); }
}
