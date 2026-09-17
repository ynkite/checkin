package idusw.sbb.checkin.domain.budget.dto;

/**
 * 축제 하나. 관광공사 KorService2/searchFestival2 에서 온다.
 *
 * 예산에 왜 필요한가 — 축제가 열리는 주말의 그 지역 숙소는 값이 오르고
 * 빨리 찬다. 「왜 이 날만 비싼가」를 말해 줄 수 있어야 한다.
 */
public record Festival(
        String contentId,
        String title,
        String startDate,   // YYYYMMDD
        String endDate,
        String address,
        String image,
        Double lat,
        Double lng
) {}
