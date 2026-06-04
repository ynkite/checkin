package idusw.sbb.triplinker.domain.weather.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeatherResponseDto {
    private String date;        // "20250615"
    private String time;        // "0600"
    private int    tmp;         // 기온 (°C)
    private int    pop;         // 강수확률 (%)
    private int    sky;         // 하늘상태 1=맑음 3=구름많음 4=흐림
    private int    pty;         // 강수형태 0=없음 1=비 3=눈 4=소나기
}