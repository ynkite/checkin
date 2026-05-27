package idusw.sbb.triplinker.domain.user.repository;

import idusw.sbb.triplinker.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    // 아이디, 이메일 중복 검사를 위한 메서드
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}