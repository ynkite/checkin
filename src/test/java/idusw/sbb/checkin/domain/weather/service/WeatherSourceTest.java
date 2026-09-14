package idusw.sbb.checkin.domain.weather.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 남은 일수 → 예보 종류 분기 검증 (E 핵심 로직). */
class WeatherSourceTest {

    @Test
    void 남은일수로_예보종류가_갈린다() {
        assertEquals("SHORT",  WeatherServiceImpl.sourceFor(0));   // 오늘
        assertEquals("SHORT",  WeatherServiceImpl.sourceFor(2));   // 2일 후
        assertEquals("MID",    WeatherServiceImpl.sourceFor(3));   // 3일 후
        assertEquals("MID",    WeatherServiceImpl.sourceFor(10));  // 10일 후
        assertEquals("NORMAL", WeatherServiceImpl.sourceFor(11));  // 11일+ → 평년
        assertEquals("NORMAL", WeatherServiceImpl.sourceFor(30));
    }
}
