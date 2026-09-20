package idusw.sbb.checkin.domain.tour.service;

import idusw.sbb.checkin.domain.crowd.AreaCode;
import idusw.sbb.checkin.domain.tour.dto.VisitorCount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * 지역 방문자 — 관광공사 DataLab(시도 단위 · 과거 실측).
 *
 * 집중률과 눈금이 다르다. 집중률은 시군구·장소 단위 예측이고 이것은 시도 단위 실측이다.
 * 둘을 한 숫자로 합치면 거짓이 된다. 화면은 나란히 두되 각각 무엇인지 적는다.
 *
 * 여행자 관점이라 외지인만 센다. 현지인 수는 여행 판단에 쓰지 않는다.
 * 기준선은 같은 요일 지난 네 번의 평균이다 — 토요일이 원래 붐비는 것과 이번 주가 유독 붐비는 것은 다르다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitorTrendService {

    public enum Status { OK, NO_DATA, UNAVAILABLE, NO_AREA }

    /**
     * @param latest    가장 최근 날짜의 외지인 수
     * @param baseline  같은 요일 지난 네 번의 평균. 표본이 없으면 null
     * @param deltaPercent 기준선 대비 몇 % 인지. 기준선이 없으면 null
     */
    public record Trend(Status status, String areaName, String latestDate, String dayOfWeek,
                        Long latest, Long baseline, Integer deltaPercent, String note) {
        static Trend of(Status s, String area, String note) {
            return new Trend(s, area, null, null, null, null, null, note);
        }
    }

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int LOOKBACK_DAYS = 70;      // 같은 요일 다섯 번을 담을 만큼
    private static final int ROWS = 3000;             // 시도 17 × 70일 × 2유형

    private final VisitorService visitorService;

    public Trend summary(String destination) {
        AreaCode.Area area = AreaCode.find(destination);
        if (area == null) return Trend.of(Status.NO_AREA, null, "이 여행지는 지역 코드가 없습니다.");

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<VisitorCount> rows;
        try {
            rows = visitorService.daily(today.minusDays(LOOKBACK_DAYS).format(YMD), today.format(YMD), ROWS, 1);
        } catch (Exception e) {
            log.warn("[방문자] 조회 실패: {}", e.getMessage());
            return Trend.of(Status.UNAVAILABLE, area.sido(), "지역 방문자 통계를 받지 못했습니다.");
        }
        if (rows.isEmpty()) return Trend.of(Status.UNAVAILABLE, area.sido(), "지역 방문자 통계를 받지 못했습니다.");

        List<VisitorCount> mine = rows.stream()
                .filter(v -> outsider(v.visitorType()))
                .filter(v -> sameArea(v, area))
                .sorted(Comparator.comparing(VisitorCount::date).reversed())
                .toList();
        if (mine.isEmpty()) {
            return Trend.of(Status.NO_DATA, area.sido(), "이 지역 방문자 통계가 아직 올라와 있지 않습니다.");
        }

        VisitorCount latest = mine.get(0);
        String dow = latest.dayOfWeek();
        List<Long> same = mine.stream()
                .skip(1)
                .filter(v -> dow != null && dow.equals(v.dayOfWeek()))
                .limit(4)
                .map(v -> Math.round(v.count()))
                .toList();

        Long baseline = same.isEmpty() ? null : Math.round(same.stream().mapToLong(Long::longValue).average().orElse(0));
        Integer delta = (baseline == null || baseline == 0) ? null
                : (int) Math.round((Math.round(latest.count()) - baseline) * 100.0 / baseline);

        String note = baseline == null
                ? "같은 요일 표본이 없어 견줄 기준이 없습니다."
                : "같은 요일 지난 " + same.size() + "번 평균과 견줬습니다.";
        return new Trend(Status.OK, area.sido(), latest.date(), dow,
                Math.round(latest.count()), baseline, delta, note);
    }

    /* touDivNm 은 「외지인」·「현지인」·「외국인」으로 온다. 여행자 관점은 외지인이다 */
    static boolean outsider(String type) {
        return type != null && type.contains("외지");
    }

    /* DataLab 은 시도 이름으로 오기도 하고 코드로 오기도 한다. 둘 다 받는다 */
    static boolean sameArea(VisitorCount v, AreaCode.Area area) {
        String code = v.areaCode() == null ? "" : v.areaCode().trim();
        if (!code.isEmpty() && code.startsWith(area.areaCd())) return true;
        String name = v.areaName() == null ? "" : v.areaName().replaceAll("\\s", "");
        return !name.isEmpty() && (name.startsWith(area.sido()) || area.sido().startsWith(name.substring(0, Math.min(2, name.length()))));
    }
}
