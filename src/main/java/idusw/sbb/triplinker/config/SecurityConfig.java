package idusw.sbb.triplinker.config;

import idusw.sbb.triplinker.domain.auth.filter.JwtAuthenticationFilter;
import idusw.sbb.triplinker.domain.auth.security.OAuth2SuccessHandler;
import idusw.sbb.triplinker.domain.auth.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

/*
* Spring Security 전역 설정 클래스
* - 애플리케이션의 모든 보안 규칙(인증, 인가, CORS, 세션 등)을 총괄
* - JWT(토큰)를 사용하기 위한 기본 세팅들을 담당
* */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    //비밀번호 암호화 도구 등록
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    //전체적인 보안 규칙을 조립하는 핵심 부분
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                //1. 기본 보안 세팅 (프론트 허용, 안 쓰는 구식 보안/세션 끄기)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                //2. API URL 주소별 접근 권한 나누기
                .authorizeHttpRequests(auth -> auth
                        //정적 리소스 (CSS, JS, 이미지 등) 전체 허용
                        .requestMatchers("/**.css", "/**.js", "/**.ico", "img/**.png", "/**.jpg", "/**.svg", "/**.woff2", "/**.woff", "/**.ttf").permitAll()

                        //인증 없이 누구나 접근 가능한 공통 API 목록(비로그인)
                        .requestMatchers(
                                "/",
                                "/error",
                                "/api/auth/signup",                 //회원가입
                                "/api/auth/login",                  //로그인
                                "/api/auth/check-username",         //아이디 중복 확인
                                "/api/auth/check-email",            //이메일 중복 확인
                                "/api/auth/send-email",             //이메일 인증 발송
                                "/api/auth/verify-email",           //이메일 인증 확인
                                "/api/auth/password/reset-request", //비밀번호 재설정 요청
                                "/api/auth/password/reset",         //비밀번호 재설정 처리
                                "/oauth2/authorization/**",         //소셜 로그인 요청
                                "/login/oauth2/code/**"             //소셜 로그인 콜백
                        ).permitAll()

                        //게시판은 보는 것(GET)만 비로그인 허용
                        .requestMatchers(HttpMethod.GET, "/api/posts").permitAll()                     // 게시글 목록
                        .requestMatchers(HttpMethod.GET, "/api/posts/*").permitAll()            // 게시글 상세
                        .requestMatchers(HttpMethod.GET, "/api/posts/*/comments").permitAll()   // 댓글 조회

                        //관리자 전용 기능
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        //API 명세서(Swagger) 접근 허용(개발 시 편하게 확인 가능)
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        //그 외의 모든 API 요청은 반드시 인증(JWT 유효성 검증) 필요
                        .anyRequest().authenticated()
                )

                //소셜 로그인 설정
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                )

                //Spring Security의 기본 인증 필터가 동작하기 전에, 커스텀 JWT 필터가 먼저 토큰을 검증하도록 설정
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    //전역 CORS(교차 출처 리소스 공유) 세부 정책 설정
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        //허용할 출처(프론트엔드 도메인) 설정 - 배포 시 특정 도메인으로 변경
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        //모든 HTTP 메서드(GET, POST, PUT, DELETE 등) 요청 허용
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        //요청 시 전달되는 모든 헤더 허용
        configuration.setAllowedHeaders(Arrays.asList("*"));

        //프론트엔드에서 응답 헤더에 담긴 'Authorization' 값을 읽을 수 있도록 노출 설정
        configuration.addExposedHeader("Authorization");
        //인증 정보(쿠키, 토큰 등)를 포함한 요청을 허용
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        //애플리케이션의 모든 엔드포인트("/**")에 위에서 정의한 CORS 정책 적용
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
