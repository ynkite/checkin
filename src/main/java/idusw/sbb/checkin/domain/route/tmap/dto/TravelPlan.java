package idusw.sbb.checkin.domain.route.tmap.dto;

import java.util.List;

/**
 * 구간별 이동 + 합계.
 *
 * ready 가 false 면 TMAP 키가 아직 없다는 뜻이다. 그때 legs 의 minutes 는 전부
 * null 이고 화면은 「이동시간 연동 전」이라고 쓴다. 0분으로 채우지 않는다.
 */
public record TravelPlan(
        boolean ready,
        String  mode,
        String  originName,
        Double  originLat,
        Double  originLng,
        List<TravelLeg> legs,
        Integer totalMinutes,
        Integer totalMeters,
        Integer totalFare,
        String  note
) {
    public static TravelPlan notReady(String mode, String originName, List<TravelLeg> legs, String note) {
        return new TravelPlan(false, mode, originName, null, null, legs, null, null, null, note);
    }
}
