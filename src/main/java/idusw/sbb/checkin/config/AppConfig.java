package idusw.sbb.checkin.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {

    // 외부 API 는 응답이 느려도 화면 전체가 멈추면 안 된다 (구동 안정성 = 심사 30점).
    // 카카오·기상청·관광공사 모두 빠른 HTTP API 라 전역 타임아웃이 안전하다.
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }
}
