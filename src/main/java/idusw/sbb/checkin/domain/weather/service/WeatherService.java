package idusw.sbb.checkin.domain.weather.service;

import idusw.sbb.checkin.domain.weather.dto.DayWeather;
import idusw.sbb.checkin.domain.weather.dto.WeatherResponseDto;

import java.time.LocalDate;
import java.util.List;

public interface WeatherService {

    /** 기존 — 오늘 기준 예보 목록 (지우지 않는다. 다른 화면이 쓴다). */
    List<WeatherResponseDto> getForecast(String region);

    /** 여행 날짜 하루의 날씨. 남은 일수로 단기/중기/평년 분기. */
    DayWeather getDayWeather(String region, LocalDate date);

    /** 캘린더용 — 날짜 범위의 날씨 (from~to, 최대 60일). 11일+ 구간은 source=NORMAL. */
    List<DayWeather> getDailyRange(String region, LocalDate from, LocalDate to);
}
