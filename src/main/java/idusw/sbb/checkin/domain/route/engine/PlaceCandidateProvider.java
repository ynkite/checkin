package idusw.sbb.checkin.domain.route.engine;

import java.time.LocalDate;
import java.util.List;

/**
 * 지역·기간을 받아 그 지역의 후보 장소 목록을 준다 — 작업 4의 경계 인터페이스 중 하나.
 *
 * <p>이 인터페이스는 "후보 선별"(연결성 데이터·필터)이 아니라 "후보 소싱"을 맡는다 —
 * 반환된 목록은 지역 안의 원재료 후보 전체이고, 앵커 기준 거리 분류·시간 배정·필터
 * 적용은 전부 그 뒤 단계(BandSplitter 이후)의 몫이다. 그래서 앵커 좌표를 받지 않는다.
 *
 * <p>{@code startDate}/{@code endDate} 는 지금(스텁) 쓰이지 않는다 — 관광공사 API가
 * 계절·행사 데이터를 구분해 줄 때(작업 4 실제 구현)를 위해 계약에 미리 넣어 둔다.
 */
public interface PlaceCandidateProvider {

    /**
     * @param region    지역명 (예: "부산", "경주", "강릉")
     * @param startDate 여행 시작일
     * @param endDate   여행 종료일
     * @return 그 지역의 후보 장소 전체 (거리·시간 분류 이전의 원재료)
     */
    List<Candidate> findCandidates(String region, LocalDate startDate, LocalDate endDate);
}
