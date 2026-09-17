package idusw.sbb.checkin.domain.detection.privacy;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 위치정보 가명처리 — 정밀 좌표를 격자 단위로 뭉갠다.
// 정밀 좌표를 서버로 보내면 위치정보사업자 신고 대상. 격자로 coarsen 하면 신고 대상에서 벗어난다.
// 규칙: 정밀 좌표는 브라우저에서만 쓰고, 서버로 넘길 때는 반드시 이걸 통과시킨다.
public final class GeoMasker {

    private GeoMasker() {}

    // 소수점 자릿수로 좌표를 격자화한다. (2자리 ≈ 1.1km, 3자리 ≈ 110m)
    // 같은 격자 안의 서로 다른 정밀 좌표는 같은 값으로 뭉개져 개인 식별이 안 된다.
    public static double[] toGrid(double lat, double lon, int decimals) {
        return new double[]{ round(lat, decimals), round(lon, decimals) };
    }

    private static double round(double v, int decimals) {
        return BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    public static void main(String[] args) {
        // 해운대 인근 정밀 좌표
        double[] g = toGrid(35.158698, 129.160384, 2);
        assert g[0] == 35.16 && g[1] == 129.16 : "2자리 격자화 틀림: " + g[0] + "," + g[1];

        // 같은 격자 안 다른 정밀 좌표 → 같은 결과 (식별 불가)
        double[] a = toGrid(35.1585, 129.1601, 2);
        double[] b = toGrid(35.1599, 129.1649, 2);
        assert a[0] == b[0] && a[1] == b[1] : "같은 격자면 같아야 한다";

        // 자릿수 줄이면 더 거칠게
        double[] c = toGrid(35.158698, 129.160384, 1);
        assert c[0] == 35.2 && c[1] == 129.2 : "1자리 격자화 틀림: " + c[0] + "," + c[1];

        System.out.println("OK 가명처리 격자화 정상 " + g[0] + "," + g[1]);
    }
}
