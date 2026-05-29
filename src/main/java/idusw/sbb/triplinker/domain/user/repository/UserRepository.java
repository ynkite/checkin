package idusw.sbb.triplinker.domain.user.repository;

import idusw.sbb.triplinker.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * User 엔티티의 데이터베이스 CRUD 및 쿼리 처리를 담당하는 Spring Data JPA 리포지토리입니다.
 * 테이블 접근에 필요한 공통 메서드를 자동으로 제공합니다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    // 유저의 로그인 ID(username)를 기반으로 회원 정보를 조회하는 메서드
    Optional<User> findByUsername(String username);
}