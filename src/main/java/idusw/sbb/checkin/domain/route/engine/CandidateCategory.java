package idusw.sbb.checkin.domain.route.engine;

/**
 * 후보 장소의 유형. 식사 슬롯에는 FOOD 만, 나머지 슬롯에는 TOUR/CAFE 가 들어간다.
 */
public enum CandidateCategory {
    TOUR,
    FOOD,
    CAFE
}
