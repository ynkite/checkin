package idusw.sbb.checkin.domain.crowd.dto;

/**
 * 한 장소·한 날짜의 혼잡도 예측.
 *
 * rate 가 null 이면 그 장소의 예측이 없다는 뜻이다. 0 으로 쓰지 않는다 —
 * 0 은 텅 비었다는 말이고 null 은 모른다는 말이다.
 *
 * source  TOUR   관광공사 집중률 예측
 *         SK     SK 실시간 (키가 붙으면)
 *         NONE   없음
 */
public record CrowdForecast(
        String  placeName,
        String  date,          // YYYYMMDD
        Double  rate,          // 0~100. 모르면 null
        String  levelKey,      // vhigh | high | mid | low | vlow. 모르면 null
        String  levelLabel,    // 매우 혼잡 … 매우 한적
        String  source,
        String  areaName,
        String  sigunguName,
        String  note
) {
    public static CrowdForecast unknown(String placeName, String date, String note) {
        return new CrowdForecast(placeName, date, null, null, null, "NONE", null, null, note);
    }
}
