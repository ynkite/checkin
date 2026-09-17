package idusw.sbb.checkin.domain.budget.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 예산 산출 요청.
 *
 * 채워 주는 것이 많을수록 정확해진다. 비면 가정으로 메우고, 무엇을 가정했는지
 * 결과에 적어 보낸다. 어림값을 확정값처럼 보여 주지 않는다.
 */
public record BudgetRequest(
        String    region,             // 「부산 해운대구」 · 「제주시」
        LocalDate from,
        LocalDate to,
        Integer   people,
        String    transport,          // CAR | TRANSIT | RENTAL. 없으면 CAR
        String    lodgingContentId,   // 관광공사 숙소 contentId. 있으면 실제 요금표를 본다
        List<String> stops,           // 들를 곳 이름. 끼니·입장료 칸 수
        Integer   totalMeters,        // 이미 계산한 총 이동거리(m). 없으면 가정
        Integer   transitFare         // 이미 계산한 대중교통 요금 합. 없으면 가정
) {
    public int peopleOr(int fallback) {
        return people == null || people < 1 ? fallback : people;
    }

    public String transportOr(String fallback) {
        return transport == null || transport.isBlank() ? fallback : transport.toUpperCase();
    }

    /** 숙박 일수. 9/18~9/20 이면 2박. */
    public int nights() {
        if (from == null || to == null || to.isBefore(from)) return 0;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(from, to);
    }

    /** 여행 일수. 9/18~9/20 이면 3일. */
    public int days() {
        return nights() + 1;
    }
}
