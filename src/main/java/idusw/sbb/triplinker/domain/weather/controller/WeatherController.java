package idusw.sbb.triplinker.domain.weather.controller;

import idusw.sbb.triplinker.domain.weather.dto.WeatherResponseDto;
import idusw.sbb.triplinker.domain.weather.service.WeatherService;
import idusw.sbb.triplinker.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/maps")
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/weather")
    public ResponseEntity<ApiResponse<List<WeatherResponseDto>>> getWeather(
            @RequestParam(defaultValue = "서울") String region) {
        List<WeatherResponseDto> forecast = weatherService.getForecast(region);
        return ResponseEntity.ok(ApiResponse.success(forecast));
    }
}