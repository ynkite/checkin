package idusw.sbb.triplinker.domain.auth.security;

import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


/*
* Spring Security 인증 과정에서 유저 데이터를 가져오는 서비스 클래스
* - 클라이언트가 로그인 요청 시 전달한 아이디(username)를 기반으로
*   DB에서 실제 유저 정보를 조회하여 SecurityContext에 전달할 UserDetails 객체를 생성
* */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    //Security가 로그인 처리를 할 때 자동으로 호출하는 메서드
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        //DB에서 입력받은 아이디(username)로 유저 조회, 존재하지 않을 경우 예외 발생
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("해당 유저를 찾을 수 없습니다: " + username));

        //조회된 엔티티를 Security 전용 객체인 CustomUserDetails로 감싸서 반환
        return new CustomUserDetails(user);
    }

    //저장된 유저 PK(ID)를 기반으로 인증 객체를 가져오는 메서드
    //로그인 시에는 String 형태의 username을 쓰지만, 토큰 검증 후에는 Long 형태의 ID를 사용하므로 추가 구현
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 ID의 유저를 찾을 수 없습니다."));
        return new CustomUserDetails(user);
    }
}