package idusw.sbb.checkin.domain.weather.controller;

import idusw.sbb.checkin.domain.weather.dto.DayWeather;
import idusw.sbb.checkin.domain.weather.dto.WeatherResponseDto;
import idusw.sbb.checkin.domain.weather.service.WeatherService;
import idusw.sbb.checkin.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/maps")
public class WeatherController {

    private final WeatherService weatherService;

    /** 기존 — 오늘 기준 예보 목록 (변경 없음). */
    @GetMapping("/weather")
    public ResponseEntity<ApiResponse<List<WeatherResponseDto>>> getWeather(
            @RequestParam(defaultValue = "서울") String region) {
        return ResponseEntity.ok(ApiResponse.success(weatherService.getForecast(region)));
    }

    /** 여행 날짜 하루의 날씨. 11일+ 는 source=NORMAL(평년). */
    @GetMapping("/weather/day")
    public ResponseEntity<ApiResponse<DayWeather>> getDayWeather(
            @RequestParam(defaultValue = "서울") String region,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(weatherService.getDayWeather(region, date)));
    }

    /** 캘린더용 — 날짜 범위(from~to)의 날씨. */
    @GetMapping("/weather/range")
    public ResponseEntity<ApiResponse<List<DayWeather>>> getWeatherRange(
            @RequestParam(defaultValue = "서울") String region,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(weatherService.getDailyRange(region, from, to)));
    }
}