package idusw.sbb.triplinker.domain.auth.filter;

import idusw.sbb.triplinker.domain.auth.security.CustomUserDetailsService;
import idusw.sbb.triplinker.domain.auth.service.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
/*
* 모든 API 요청마다 클라이언트가 보낸 JWT 토큰을 검사하는 인증 필터
* - HTTP 요청 헤더에서 토큰을 추출하고 유효성을 검증
* - 토큰이 정상적이라면 유저 정보를 조회하여 Spring Security Context에 인증 상태를 저장
* */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService customUserDetailsService;

    //모든 API 요청이 들어올 때마다 가로채서 실행되는 핵심 검증 로직
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        //1. 요청 헤더에서 순수 토큰만 추출
        String token = resolveToken(request);

        //2. 유효한 토큰인지 확인
        if (token != null && jwtProvider.validateToken(token)) {

            //3. 유효한 토큰에서 유저의 고유 ID를 꺼냄
            Long userId = jwtProvider.getUserIdFromToken(token);

            //4. DB에서 해당 ID를 가진 유저의 정보(UserDetails)를 조회
            UserDetails userDetails = customUserDetailsService.loadUserById(userId);

            //5. Spring Security가 알아볼 수 있는 형태의 '인증 통행증(Token)' 객체 생성
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            //6. SecurityContext(보안 전용 보관소)에 방금 만든 통행증을 저장하여 '로그인된 상태'임을 보장
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        //7. 다음 필터나 목적지(Controller)로 요청을 넘겨줌
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
