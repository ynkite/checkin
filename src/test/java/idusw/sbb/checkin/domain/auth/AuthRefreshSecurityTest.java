package idusw.sbb.checkin.domain.auth;

import idusw.sbb.checkin.config.ApiAuthenticationEntryPoint;
import idusw.sbb.checkin.config.SecurityConfig;
import idusw.sbb.checkin.domain.auth.controller.AuthController;
import idusw.sbb.checkin.domain.auth.dto.TokenResponseDto;
import idusw.sbb.checkin.domain.auth.security.OAuth2FailureHandler;
import idusw.sbb.checkin.domain.auth.security.OAuth2SuccessHandler;
import idusw.sbb.checkin.domain.auth.service.AuthService;
import idusw.sbb.checkin.domain.auth.service.CustomOAuth2UserService;
import idusw.sbb.checkin.domain.auth.service.EmailAuthService;
import idusw.sbb.checkin.domain.auth.service.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** access 토큰이 만료된 뒤 부르는 재발급. 보안 설정에 막히거나 프론트가 못 읽는 모양으로 답하면 곧바로 강제 로그아웃이 된다 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class})
@TestPropertySource(properties = {"file.upload.path=build/tmp/uploads/", "file.upload.url=/uploads/"})
class AuthRefreshSecurityTest {

    @Autowired MockMvc mvc;

    @MockitoBean AuthService authService;
    @MockitoBean EmailAuthService emailAuthService;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean idusw.sbb.checkin.domain.user.repository.UserRepository userRepository;  // JWT 필터가 지금 역할·상태를 읽는다
    @MockitoBean CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean OAuth2SuccessHandler oAuth2SuccessHandler;
    @MockitoBean OAuth2FailureHandler oAuth2FailureHandler;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;  // 앱 클래스의 JPA Auditing 이 웹 계층 테스트에서도 켜져서

    @Test
    void 재발급은_access_토큰_없이_부르고_login_과_같은_모양으로_답한다() throws Exception {
        given(authService.refresh("r1")).willReturn(new TokenResponseDto("a2", "r1", false));

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"r1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("a2"));
    }

    @Test
    void 틀린_refresh_토큰은_보안_설정이_아니라_서비스가_거절한다() throws Exception {
        given(authService.refresh("bad")).willThrow(new IllegalArgumentException("유효하지 않은 토큰입니다."));

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 다른_인증_API_는_그대로_막혀_있다() throws Exception {
        mvc.perform(post("/api/auth/pw-noti-later"))
                .andExpect(status().isUnauthorized());
    }
}
