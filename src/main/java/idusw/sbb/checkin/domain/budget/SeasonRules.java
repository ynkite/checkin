package idusw.sbb.checkin.domain.budget;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Set;

/**
 * 성수기 판정 (순수 함수).
 *
 * 무엇에 배수를 걸고 무엇에 안 거는가 —
 *
 *   숙박   배수를 걸지 않는다. LodgingRateService 가 관광공사 요금표의
 *          「성수기 주말 / 비수기 주중」 칸을 이미 골라 쓴다. 거기에 또 곱하면
 *          같은 성수기를 두 번 세는 것이 된다
 *   렌터카 걸린다. 제주 성수기 렌터카는 실제로 두 배 가까이 간다
 *   대중교통·유류비 안 걸린다. 요금이 계절로 바뀌지 않는다
 *   식비   조금 걸린다. 관광지 식당은 성수기에 오른다
 *   입장료 안 걸린다
 *
 * 음력 명절은 계산하지 않고 2026~2028 연휴를 적어 둔다. 음력 변환을 직접
 * 짜면 그것만 버그가 난다. 세 해면 마감(2026-09)과 심사까지 넉넉하다.
 */
public final class SeasonRules {

    private SeasonRules() {}

    /** 한 날짜의 성수기 등급. */
    public record Season(String key, String label, double carMultiplier, double foodMultiplier) {
        public boolean peak() { return carMultiplier > 1.0; }
    }

    public static final Season OFF = new Season("off", "평시", 1.00, 1.00);
    private static final Season SUMMER_TOP = new Season("summer-top", "여름 극성수기", 2.00, 1.12);
    private static final Season SUMMER     = new Season("summer", "여름 성수기", 1.60, 1.08);
    private static final Season NEWYEAR    = new Season("newyear", "연말연시", 1.45, 1.08);
    private static final Season HOLIDAY    = new Season("holiday", "명절 연휴", 1.40, 1.05);
    private static final Season BLOSSOM    = new Season("blossom", "벚꽃철", 1.25, 1.03);
    private static final Season AUTUMN     = new Season("autumn", "단풍철", 1.25, 1.03);

    /* 명절 연휴 — 설·추석 앞뒤를 포함한다. 음력 변환은 하지 않는다. */
    private static final Set<LocalDate> HOLIDAYS = Set.of(
            // 2026 설 (2/16~2/18) + 앞뒤
            LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17),
            LocalDate.of(2026, 2, 18), LocalDate.of(2026, 2, 19),
            // 2026 추석 (9/24~9/26) + 앞뒤
            LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25),
            LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 27),
            // 2027 설 (2/6~2/8)
            LocalDate.of(2027, 2, 5), LocalDate.of(2027, 2, 6), LocalDate.of(2027, 2, 7),
            LocalDate.of(2027, 2, 8), LocalDate.of(2027, 2, 9),
            // 2027 추석 (9/14~9/16)
            LocalDate.of(2027, 9, 13), LocalDate.of(2027, 9, 14), LocalDate.of(2027, 9, 15),
            LocalDate.of(2027, 9, 16), LocalDate.of(2027, 9, 17),
            // 2028 설 (1/26~1/28)
            LocalDate.of(2028, 1, 25), LocalDate.of(2028, 1, 26), LocalDate.of(2028, 1, 27),
            LocalDate.of(2028, 1, 28), LocalDate.of(2028, 1, 29),
            // 2028 추석 (10/2~10/4)
            LocalDate.of(2028, 10, 1), LocalDate.of(2028, 10, 2), LocalDate.of(2028, 10, 3),
            LocalDate.of(2028, 10, 4), LocalDate.of(2028, 10, 5));

    public static Season of(LocalDate d) {
        if (d == null) return OFF;
        if (HOLIDAYS.contains(d)) return HOLIDAY;

        MonthDay md = MonthDay.from(d);
        if (between(md, 7, 20, 8, 17)) return SUMMER_TOP;
        if (between(md, 7, 10, 7, 19) || between(md, 8, 18, 8, 24)) return SUMMER;
        if (between(md, 12, 24, 12, 31) || between(md, 1, 1, 1, 2)) return NEWYEAR;
        if (between(md, 4, 1, 4, 12)) return BLOSSOM;
        if (between(md, 10, 18, 11, 2)) return AUTUMN;
        return OFF;
    }

    /** 체크인이 금·토면 주말 요금이다. LodgingRateService 와 같은 기준을 쓴다. */
    public static boolean weekendCheckIn(LocalDate d) {
        if (d == null) return false;
        DayOfWeek w = d.getDayOfWeek();
        return w == DayOfWeek.FRIDAY || w == DayOfWeek.SATURDAY;
    }

    /** 여행 기간에서 가장 센 성수기. 며칠이 성수기면 숙소가 그 값으로 잡힌다. */
    public static Season strongest(LocalDate from, LocalDate to) {
        if (from == null) return OFF;
        LocalDate end = to == null || to.isBefore(from) ? from : to;
        Season best = OFF;
        for (LocalDate d = from; !d.isAfter(end); d = d.plusDays(1)) {
            Season s = of(d);
            if (s.carMultiplier() > best.carMultiplier()) best = s;
        }
        return best;
    }

    /** 기간 안의 성수기 날짜들. 화면이 「어느 날이 비싸다」를 말할 수 있게. */
    public static List<LocalDate> peakDays(LocalDate from, LocalDate to) {
        if (from == null) return List.of();
        LocalDate end = to == null || to.isBefore(from) ? from : to;
        List<LocalDate> out = new java.util.ArrayList<>();
        for (LocalDate d = from; !d.isAfter(end); d = d.plusDays(1)) {
            if (of(d).peak()) out.add(d);
        }
        return out;
    }

    private static boolean between(MonthDay md, int m1, int d1, int m2, int d2) {
        MonthDay a = MonthDay.of(m1, d1), b = MonthDay.of(m2, d2);
        return !md.isBefore(a) && !md.isAfter(b);
    }

    public static void main(String[] args) {
        assert of(LocalDate.of(2026, 8, 1)).key().equals("summer-top") : "8/1 극성수기";
        assert of(LocalDate.of(2026, 7, 19)).key().equals("summer") : "7/19 성수기";
        assert of(LocalDate.of(2026, 7, 20)).key().equals("summer-top") : "7/20 부터 극성수기";
        assert of(LocalDate.of(2026, 8, 18)).key().equals("summer") : "8/18 성수기로 내려온다";
        assert of(LocalDate.of(2026, 9, 17)).key().equals("off") : "9/17 평시";
        assert of(LocalDate.of(2026, 9, 25)).key().equals("holiday") : "추석";
        assert of(LocalDate.of(2026, 12, 31)).key().equals("newyear") : "연말";
        assert of(LocalDate.of(2027, 1, 2)).key().equals("newyear") : "연초";
        assert of(LocalDate.of(2026, 1, 3)).key().equals("off") : "1/3 은 평시";
        assert of(LocalDate.of(2026, 4, 5)).key().equals("blossom") : "벚꽃";
        assert of(LocalDate.of(2026, 10, 25)).key().equals("autumn") : "단풍";
        assert of(null) == OFF : "날짜가 없으면 평시";

        assert weekendCheckIn(LocalDate.of(2026, 9, 18)) : "금요일은 주말 요금";
        assert weekendCheckIn(LocalDate.of(2026, 9, 19)) : "토요일도";
        assert !weekendCheckIn(LocalDate.of(2026, 9, 20)) : "일요일 체크인은 주중";

        assert strongest(LocalDate.of(2026, 8, 16), LocalDate.of(2026, 8, 19))
                .key().equals("summer-top") : "기간 안 가장 센 것";
        assert peakDays(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 18)).isEmpty()
                : "평시면 성수기 날짜가 없다";
        assert peakDays(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 27)).size() == 5
                : "추석 닷새";
        System.out.println("OK 성수기 판정");
    }
}
