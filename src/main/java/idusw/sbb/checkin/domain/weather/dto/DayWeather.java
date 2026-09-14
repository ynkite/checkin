package idusw.sbb.checkin.domain.weather.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 날짜별(하루 1건) 날씨 — 캘린더·여행 날짜 타깃팅용.
 * source 로 화면이 「평년 기준」 표기 여부를 정한다.
 */
@Getter
@Builder
public class DayWeather {
    private String  date;          // "20260919"
    private String  source;        // SHORT(0~2일) | MID(3~10일) | NORMAL(11일+)
    private Integer tempMin;       // 최저기온 (평년이면 평년값)
    private Integer tempMax;       // 최고기온
    private Integer rainProb;      // 강수확률 %. 평년값이면 null
    private String  sky;           // 맑음 / 구름많음 / 흐림
    private boolean rainExpected;  // 동선 엔진용 — 비 예보 플래그
}
