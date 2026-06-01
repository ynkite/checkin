package idusw.sbb.triplinker.domain.auth.security;

import idusw.sbb.triplinker.domain.auth.entity.RefreshToken;
import idusw.sbb.triplinker.domain.auth.repository.RefreshTokenRepository;
import idusw.sbb.triplinker.domain.auth.service.JwtProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        CustomUserDetails oAuth2User = (CustomUserDetails) authentication.getPrincipal();

        Long userId = Long.valueOf(oAuth2User.getName());

        //JWT 토큰 발급
        String accessToken = jwtProvider.createAccessToken(userId, "USER");
        String refreshToken = jwtProvider.createRefreshToken(userId);

        //리프레시 토큰을 해싱해서 DB에 저장
        refreshTokenRepository.deleteByUserId(userId);
        String hashedRefreshToken = hashSha256(refreshToken);
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(hashedRefreshToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build());

        //현재 서버 주소로 동적 변경
        //토큰을 쿼리 파라미터로 담아 현재 서버의 루트 페이지로 리다이렉트
        //app_main.js의 _handleOAuthCallback()이 URL 파라미터에서 토큰을 읽어 처리
        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() != 80 && request.getServerPort() != 443 ? ":" + request.getServerPort() : "")
                + "/";
        String targetUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("토큰 해싱 중 오류가 발생했습니다.", e);
        }
    }
}
