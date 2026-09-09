package idusw.sbb.checkin.domain.user.repository;

import idusw.sbb.checkin.domain.user.entity.UserSecurityHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSecurityHistoryRepository
        extends JpaRepository<UserSecurityHistory, Long> {

    List<UserSecurityHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}