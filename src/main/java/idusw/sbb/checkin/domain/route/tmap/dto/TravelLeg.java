package idusw.sbb.checkin.domain.route.tmap.dto;

/**
 * 한 구간의 이동 — 어디서 어디로, 얼마나 걸리고, 얼마 드는가.
 *
 * mode  CAR | TRANSIT | WALK | NONE
 * NONE 은 길을 못 찾았거나 TMAP 키가 아직 없는 경우다. 0분이라고 하지 않는다 —
 * minutes 가 null 이면 화면은 「이동시간 확인 중」으로 둔다.
 */
public record TravelLeg(
        String  fromName,
        String  toName,
        String  mode,
        Integer minutes,     // 분. 모르면 null
        Integer meters,      // m. 모르면 null
        Integer fare,        // 원 (대중교통 요금 · 자동차 통행료). 모르면 null
        Integer transfers,   // 환승 횟수 (대중교통만)
        String  note         // 사람이 읽는 한 줄. 「지금 교통량 기준」 같은 것
) {
    public static TravelLeg unknown(String from, String to, String note) {
        return new TravelLeg(from, to, "NONE", null, null, null, null, note);
    }
}
