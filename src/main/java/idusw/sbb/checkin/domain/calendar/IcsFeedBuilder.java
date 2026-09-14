package idusw.sbb.checkin.domain.calendar;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

// 여행 일정을 iCalendar(.ics) 문자열로 만든다.
// 시각은 KST(Asia/Seoul)로 들어오지만 UTC로 변환해 내보낸다 — VTIMEZONE 블록 없이 모든 클라이언트가 정확히 읽는다.
public final class IcsFeedBuilder {

    // 한 개의 일정 항목
    public record CalEvent(String uid, String summary, LocalDateTime startKst) {}

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final int DEFAULT_DURATION_MIN = 60;
    private static final int ALARM_BEFORE_MIN = 30;

    private IcsFeedBuilder() {}

    public static String build(String calendarName, List<CalEvent> events) {
        String stamp = LocalDateTime.now(ZoneOffset.UTC).format(UTC);
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//checkin//trip//KO");
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "METHOD:PUBLISH");
        line(sb, "X-WR-CALNAME:" + escape(calendarName));
        for (CalEvent e : events) {
            String start = toUtc(e.startKst());
            String end = toUtc(e.startKst().plusMinutes(DEFAULT_DURATION_MIN));
            line(sb, "BEGIN:VEVENT");
            line(sb, "UID:" + e.uid() + "@checkin");
            line(sb, "DTSTAMP:" + stamp);
            line(sb, "DTSTART:" + start);
            line(sb, "DTEND:" + end);
            line(sb, "SUMMARY:" + escape(e.summary()));
            line(sb, "BEGIN:VALARM");
            line(sb, "TRIGGER:-PT" + ALARM_BEFORE_MIN + "M");
            line(sb, "ACTION:DISPLAY");
            line(sb, "DESCRIPTION:" + escape(e.summary() + " 일정 알림"));
            line(sb, "END:VALARM");
            line(sb, "END:VEVENT");
        }
        line(sb, "END:VCALENDAR");
        return sb.toString();
    }

    private static String toUtc(LocalDateTime kst) {
        return kst.atZone(KST).withZoneSameInstant(ZoneOffset.UTC).format(UTC);
    }

    // iCalendar 텍스트 이스케이프: \ ; , 개행
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }

    // ponytail: 75옥텟 라인 폴딩 생략. 클라이언트가 긴 SUMMARY를 거부하면 그때 폴딩 추가.
    private static void line(StringBuilder sb, String content) {
        sb.append(content).append("\r\n");
    }

    // 자체 검증
    public static void main(String[] args) {
        CalEvent e = new CalEvent("p1-d1-0", "협재; 수우동, 맛집", LocalDateTime.of(2026, 9, 19, 15, 0));
        String ics = build("체크인 여행 일정", List.of(e));
        assert ics.startsWith("BEGIN:VCALENDAR\r\n") : "헤더 시작";
        assert ics.endsWith("END:VCALENDAR\r\n") : "푸터 끝";
        // KST 15:00 → UTC 06:00
        assert ics.contains("DTSTART:20260919T060000Z") : "UTC 변환 틀림: " + ics;
        assert ics.contains("SUMMARY:협재\\; 수우동\\, 맛집") : "이스케이프 틀림";
        assert ics.contains("TRIGGER:-PT30M") : "알람 누락";
        assert ics.contains("UID:p1-d1-0@checkin") : "UID 누락";
        System.out.println("OK\n" + ics);
    }
}
