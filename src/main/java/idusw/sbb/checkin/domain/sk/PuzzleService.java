package idusw.sbb.checkin.domain.sk;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 지오비전 퍼즐 일곱 가지를 우리 말로 감싼다.
 *
 * 왜 감싸는가 — 화면이 SK 의 경로 문자열을 직접 알 필요가 없다.
 * 나중에 SK 가 경로를 바꿔도 여기만 고치면 된다.
 *
 * 경로는 설정으로 뺐다. 신청한 상품마다 버전이 달라서 문서를 보고
 * 맞춰야 하는데, 그때 자바를 다시 빌드하지 않게 하려는 것이다.
 * 기본값은 개발 가이드 기준이고, 다르면 properties 에서 덮는다.
 *
 * 전부 「없으면 null」이다. 키가 없거나 상품을 아직 안 받았으면
 * 화면은 그 자리를 비우고 「연동 전」이라고 쓴다. 0 으로 채우지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PuzzleService {

    private final PuzzleClient client;
    private final PuzzlePaths paths;

    public boolean ready() {
        return client.ready();
    }

    /** 지하철 역별 시간대 혼잡도. 「지하철로 가면 그 시각에 얼마나 붐비나」 */
    public JsonNode subwayCongestion(String stationCode, String yyyymmdd) {
        return client.get(paths.subway().replace("{station}", nz(stationCode)), q("date", yyyymmdd));
    }

    /** 장소(상권) 혼잡도. 관광공사 집중률과 교차 검증에 쓴다 */
    public JsonNode placeCongestion(String poiId, String yyyymmdd) {
        return client.get(paths.place().replace("{poi}", nz(poiId)), q("date", yyyymmdd));
    }

    /** 유동인구. 그 지역에 사람이 실제로 얼마나 다녔는가 */
    public JsonNode population(String areaCode, String yyyymmdd) {
        return client.get(paths.pop().replace("{area}", nz(areaCode)), q("date", yyyymmdd));
    }

    /** 국내 여행 — 방문 특성. 성수기 판단과 대체 후보 고를 때 */
    public JsonNode travel(String areaCode, String yyyymm) {
        return client.get(paths.travel().replace("{area}", nz(areaCode)), q("month", yyyymm));
    }

    /** 음식점 — 상권 소비. 예산의 식비 단가를 실제 값으로 바꿀 때 */
    public JsonNode dining(String areaCode, String yyyymm) {
        return client.get(paths.dining().replace("{area}", nz(areaCode)), q("month", yyyymm));
    }

    /** 주거 생활 — 그 동네가 관광지인지 생활권인지 가른다 */
    public JsonNode residence(String areaCode, String yyyymm) {
        return client.get(paths.residence().replace("{area}", nz(areaCode)), q("month", yyyymm));
    }

    /** 학원 — 방학·학기 주기를 읽는다. 가족 여행 성수기와 겹친다 */
    public JsonNode academy(String areaCode, String yyyymm) {
        return client.get(paths.academy().replace("{area}", nz(areaCode)), q("month", yyyymm));
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static Map<String, String> q(String k, String v) {
        Map<String, String> m = new LinkedHashMap<>();
        if (v != null && !v.isBlank()) m.put(k, v);
        return m;
    }
}
