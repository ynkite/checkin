package idusw.sbb.triplinker.domain.weather.service;

import idusw.sbb.triplinker.domain.weather.dto.WeatherResponseDto;

import java.util.List;

public interface WeatherService {
    List<WeatherResponseDto> getForecast(String region);
}