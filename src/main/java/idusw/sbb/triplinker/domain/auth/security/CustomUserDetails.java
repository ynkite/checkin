package idusw.sbb.triplinker.domain.auth.security;

import idusw.sbb.triplinker.domain.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/*
* Spring Security에서 사용하는 유저 정보 객체 (어댑터 클래스)
* - 애플리케이션의 User 엔티티와 Spring Security의 UserDetails 인터페이스를 연결
* - Security 인증 과정에서 필요한 유저의 권한 및 계정 상태 정보를 제공
* */

public class CustomUserDetails implements UserDetails, OAuth2User {

    @Getter
    private final User user;
    private final Map<String, Object> attributes;

    // JWT 전용 필드 (DB 조회 없이 토큰 클레임으로 생성할 때 사용)
    private final Long tokenUserId;
    private final String tokenRole;

    public CustomUserDetails(User user) {
        this.user = user;
        this.attributes = Collections.emptyMap();
        this.tokenUserId = null;
        this.tokenRole = null;
    }

    public CustomUserDetails(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
        this.tokenUserId = null;
        this.tokenRole = null;
    }

    // JWT 필터 전용 — DB 조회 없이 토큰 클레임만으로 생성
    public CustomUserDetails(Long userId, String role) {
        this.user = null;
        this.tokenUserId = userId;
        this.tokenRole = role;
        this.attributes = Collections.emptyMap();
    }

    // user ID를 안전하게 꺼내는 편의 메서드
    public Long getUserId() {
        return user != null ? user.getId() : tokenUserId;
    }

    //유저의 권한(Role)을 Security에 전달
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String role = user != null ? user.getRole() : tokenRole;
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return user != null ? user.getPasswordHash() : null;
    }

    // JWT 전용 생성자로 만들어진 경우 username 대신 userId 문자열 반환
    @Override
    public String getUsername() {
        return user != null ? user.getUsername() : String.valueOf(tokenUserId);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user == null || !"SUSPENDED".equals(user.getStatus());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user == null || "ACTIVE".equals(user.getStatus());
    }

    @Override
    public String getName() {
        return String.valueOf(getUserId());
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes != null ? attributes : Collections.emptyMap();
    }
}
