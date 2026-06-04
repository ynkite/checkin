package idusw.sbb.triplinker.config;

import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("local")
@RequiredArgsConstructor
public class DummyDataInit implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {

        //"test"라는 아이디가 DB에 없을 때만
        if (userRepository.findByUsername("test").isEmpty()) {

            User dummyUser = User.builder()
                    .username("test")
                    .passwordHash(passwordEncoder.encode("1234"))
                    .name("김테스트")
                    .email("test@test")
                    .region("서울")
                    .role("USER")
                    .status("ACTIVE")
                    .lastPwChangedAt(LocalDateTime.now())
                    .build();

            userRepository.save(dummyUser);

            System.out.println("========테스트용 더미 유저 생성 완료=========");
        }
    }
}