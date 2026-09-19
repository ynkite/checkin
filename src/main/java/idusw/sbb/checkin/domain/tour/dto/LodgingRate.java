package idusw.sbb.checkin.domain.tour.dto;

/**
 * 숙박 1박 요금 추정 결과 (D 확장).
 * 관광공사 detailInfo2 에 요금이 있으면 확정, 없으면 지역 평균으로 추정한다.
 */
public record LodgingRate(
        long amount,          // 1박 금액 (원)
        boolean estimated,    // true=추정(지역평균), false=확정(공개요금)
        String basis,         // 근거 문구. 예: "공개 요금", "지역 평균 (N=12)"
        String season,        // "성수기 주말" / "비수기 주중" 등
        int sampleSize        // 지역 평균 표본 수 N. 확정·없음이면 0. 화면이 N 을 함께 띄운다.
) {
    public static LodgingRate confirmed(long amount, String season) {
        return new LodgingRate(amount, false, "공개 요금", season, 0);
    }
    public static LodgingRate estimated(long amount, int sampleSize, String season) {
        return new LodgingRate(amount, true, "지역 평균 (N=" + sampleSize + ")", season, sampleSize);
    }
    public static LodgingRate none(String season) {
        return new LodgingRate(0, true, "요금 정보 없음", season, 0);
    }
}
