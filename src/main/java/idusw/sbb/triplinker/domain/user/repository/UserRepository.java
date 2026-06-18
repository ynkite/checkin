package idusw.sbb.triplinker.domain.user.repository;

import idusw.sbb.triplinker.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

//  User 엔티티의 데이터베이스 CRUD 및 쿼리 처리를 담당하는 Spring Data JPA 리포지토리
//  테이블 접근에 필요한 공통 메서드를 자동으로 제공


public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    // 관리자 - 상태별 회원 목록 조회 (페이징)
    Page<User> findByStatus(String status, Pageable pageable);

    // 관리자 대시보드 - 상태별 회원 수 집계
    long countByStatus(String status);

    // 관리자 통계 - 기간별 신규 가입자 수 집계
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 관리자 통계 - 일자별 신규 가입자 수 (추이 차트용)
    @Query("SELECT FUNCTION('DATE', u.createdAt), COUNT(u) FROM User u " +
            "WHERE u.createdAt BETWEEN :start AND :end GROUP BY FUNCTION('DATE', u.createdAt)")
    List<Object[]> countDailyNewUsers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}