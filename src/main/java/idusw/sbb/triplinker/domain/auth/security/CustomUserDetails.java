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

    //애플리케이션 내에서 User 엔티티 정보가 필요할 때 쉽게 꺼내쓰기 위한 메서드
    @Getter
    private final User user;
    private final Map<String, Object> attributes;

    public CustomUserDetails(User user) {
        this.user = user;
        this.attributes = Collections.emptyMap();
    }

    public CustomUserDetails(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    //유저의 권한(Role)을 Security에 전달
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        //Security 규칙에 맞춰 DB의 role 값 앞에 "ROLE_" 접두사를 붙여서 반환
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }

    //유저의 암호화된 비밀번호 반환
    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    //유저의 고유 식별자(아이디) 반환
    @Override
    public String getUsername() {
        return user.getUsername();
    }

    //계정 만료 여부 (true : 만료 안됨)
    @Override
    public boolean isAccountNonExpired() {
        //기획상 기간제 계정 기능이 없으므로 항상 true 유지
        return true;
    }

    //계정 잠금 여부 (true : 잠금 안 됨)
    @Override
    public boolean isAccountNonLocked() {
        //DB의 status가 "SUSPENDED(정지)"일 경우 false를 반환하여 로그인을 차단
        return !"SUSPENDED".equals(user.getStatus());
    }

    //비밀번호 만료 여부 (true: 만료 안됨)
    @Override
    public boolean isCredentialsNonExpired() {
        //비밀번호 변경 주기가 지났더라도 강제 로그아웃/만료 처리를 하지 않으므로 true로 고정
        return true;
    }

    //계정 활성화 여부 (true : 활성화 됨)
    @Override
    public boolean isEnabled() {
        //DB의 status가 "ACTIVE"인 정상 회원만 true 반환 (탈퇴 회원 구분)
        return "ACTIVE".equals(user.getStatus());
    }

    @Override
    public String getName() {
        return String.valueOf(user.getId());
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes != null ? attributes : Collections.emptyMap();
    }
}
