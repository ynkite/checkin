package idusw.sbb.triplinker.domain.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtProvider {

    private final Key key;
    private final long accessTokenValidityTime;
    private final long refreshTokenValidityTime;

    //application.properties에 적어둔 값으로 세팅
    public JwtProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.access-token-validity-in-milliseconds}") long accessTokenValidityTime,
            @Value("${jwt.refresh-token-validity-in-milliseconds}") long refreshTokenValidityTime
    ) {
        byte[] keyBytes = Base64.getDecoder().decode(Base64.getEncoder().encodeToString(secretKey.getBytes()));
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenValidityTime = accessTokenValidityTime;
        this.refreshTokenValidityTime = refreshTokenValidityTime;
    }

    //Access Token 발급 (유저의 아이디와 권한 정보 담기)
    public String createAccessToken(Long userId, String role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + accessTokenValidityTime);

        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    //Refresh Token 발급
    public String createRefreshToken(Long userId) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + refreshTokenValidityTime);

        return Jwts.builder()
                .setSubject(userId.toString())
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    //토큰이 유효한지 검사
    public boolean validateToken(String token) {
        try {
            //Key를 가지고 토큰을 확인함. 조작되었거나 만료되었으면 에러 터짐
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true; //에러 안터지고 넘어가면 true를 반환(유효한 토큰)
        } catch (JwtException | IllegalArgumentException e) {
            //만료되었거나 이상한 토큰이면 false를 반환
            return false;
        }
    }

    //유효한 토큰에서 유저 아이디(PK) 꺼내기
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        //setSubject에 넣었던 userId를 꺼내서 반환
        return Long.parseLong(claims.getSubject());
    }
}