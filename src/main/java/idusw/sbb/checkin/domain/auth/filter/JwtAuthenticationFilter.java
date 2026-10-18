package idusw.sbb.checkin.domain.auth.filter;

import idusw.sbb.checkin.domain.auth.security.CustomUserDetails;
import idusw.sbb.checkin.domain.auth.service.JwtProvider;
import idusw.sbb.checkin.domain.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 모든 API 요청마다 클라이언트가 보낸 JWT 토큰을 검사하는 인증 필터
// HTTP 요청 헤더에서 토큰을 추출하고 유효성을 검증
// 토큰이 정상적이라면 유저 정보를 조회하여 Spring Security Context에 인증 상태를 저장

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    //모든 API 요청이 들어올 때마다 가로채서 실행되는 핵심 검증 로직
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 요청 헤더에서 순수 토큰만 추출
        String token = resolveToken(request);

        // 유효한 토큰인지 확인. refresh 토큰에는 role 이 없다 — access 토큰 자리에 쓰지 못하게 거른다
        if (token != null && jwtProvider.validateToken(token) && jwtProvider.getRoleFromToken(token) != null) {

            // 역할·상태는 토큰이 아니라 지금 DB 값을 쓴다. 관리자 권한을 거둔 뒤에도
            // 예전 토큰으로 관리자 API 를 부를 수 있었다. 탈퇴한 계정은 인증하지 않는다.
            // 정지(SUSPENDED)는 인증은 하되 글·댓글만 막는 정책이라 여기서 거르지 않는다
            userRepository.findById(jwtProvider.getUserIdFromToken(token))
                    .filter(u -> !"DELETED".equals(u.getStatus()))
                    .ifPresent(u -> {
                        CustomUserDetails userDetails = new CustomUserDetails(u.getId(), u.getRole());
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
                    });
        }

        // 다음 필터나 목적지(Controller)로 요청을 넘겨줌
        filterChain.doFilter(request, response);
    }

    //HTTP 요청 헤더에서 'Bearer ' 글자를 떼어내고 순수 토큰 값만 잘라내는 메서드
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        //헤더에 값이 있고, "Bearer "로 시작하는 정상적인 토큰 형태인지 확인
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
