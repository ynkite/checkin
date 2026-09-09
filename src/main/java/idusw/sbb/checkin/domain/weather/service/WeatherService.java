package idusw.sbb.checkin.domain.weather.service;

import idusw.sbb.checkin.domain.weather.dto.WeatherResponseDto;

import java.util.List;

public interface WeatherService {
    List<WeatherResponseDto> getForecast(String region);
}