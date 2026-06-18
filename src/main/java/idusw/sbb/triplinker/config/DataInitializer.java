package idusw.sbb.triplinker.config;

import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// 서버 최초 기동 시 관리자(admin) 계정이 없으면 하나 자동으로 만들어주는 초기화 클래스
// 회원가입 화면(SignUpRequestDTO의 비밀번호 정규식 검증)을 거치지 않고
// Repository에 직접 저장하기 때문에, "1234"처럼 단순한 비밀번호도 그대로 사용 가능하다.
//   회원가입 DTO 단계에서만 일어나고, User 엔티티 자체에는 그 제약이 없기 때문에 별도의 예외처리를 하지 않고 비밀번호를 1234로 지정 가능
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.existsByUsername("admin")) {
            log.info("관리자 계정이 있으므로 추가하지 않겠습니다.");
            return;
        }

        userRepository.save(User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("1234"))
                .name("관리자")
                .email("admin@triplinker.com")
                .region("서울")
                .role("ADMIN")
                .status("ACTIVE")
                .lastPwChangedAt(LocalDateTime.now())
                .build());

        log.info("관리자 계정이 생성되었습니다.");
    }
}