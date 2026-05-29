package idusw.sbb.triplinker.domain.auth.service;

import idusw.sbb.triplinker.domain.auth.dto.SignUpRequestDTO;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean checkUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public void signUp(SignUpRequestDTO dto) {
        // 최종 중복 검증
        if (checkUsername(dto.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (checkEmail(dto.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // DTO -> Entity 변환
        User user = User.builder()
                .username(dto.getUsername())
                .passwordHash(dto.getPassword())
                .name(dto.getName())
                .email(dto.getEmail())
                .region(dto.getRegion())
                .birthDate(dto.getBirthDate())
                .gender(dto.getGender())
                .mbti(dto.getMbti())
                .build();

        // DB 저장
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void updatePassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        // User 엔티티의 비밀번호 변경 메서드 호출
        user.updatePassword(newPassword);
    }
}