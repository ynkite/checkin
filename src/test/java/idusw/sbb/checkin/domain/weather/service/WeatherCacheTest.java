package idusw.sbb.checkin.domain.weather.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 일정 저장마다 날짜 수만큼 기상청을 다시 부르던 것 — 10분은 같은 예보를 쓴다. */
class WeatherCacheTest {

    private final RestTemplate rest = mock(RestTemplate.class);
    private final WeatherServiceImpl svc = new WeatherServiceImpl(rest, new ObjectMapper());
    private final String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    @Test
    void 단기예보는_한_번_받아_날마다_나눠_쓴다() {
        String body = "{\"response\":{\"body\":{\"items\":{\"item\":["
                + "{\"fcstDate\":\"" + today + "\",\"fcstTime\":\"1200\",\"category\":\"TMP\",\"fcstValue\":\"18\"},"
                + "{\"fcstDate\":\"" + today + "\",\"fcstTime\":\"1200\",\"category\":\"POP\",\"fcstValue\":\"70\"}]}}}}";
        when(rest.getForObject(contains("getVilageFcst"), eq(String.class))).thenReturn(body);

        LocalDate d = LocalDate.now();
        assertEquals(70, svc.getDayWeather("서울", d).getRainProb());
        assertEquals(70, svc.getDayWeather("서울", d).getRainProb());
        svc.getDayWeather("서울", d.plusDays(1));

        verify(rest, times(1)).getForObject(contains("getVilageFcst"), eq(String.class));
    }

    @Test
    void 못_받으면_평년값으로_답하고_다음에_다시_묻는다() {
        when(rest.getForObject(anyString(), eq(String.class))).thenThrow(new RestClientException("기상청 장애"));

        assertEquals("NORMAL", svc.getDayWeather("서울", LocalDate.now()).getSource());
        assertEquals("NORMAL", svc.getDayWeather("서울", LocalDate.now()).getSource());

        verify(rest, times(2)).getForObject(contains("getVilageFcst"), eq(String.class));
    }
}
