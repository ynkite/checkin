package idusw.sbb.checkin.domain.push.repository;

import idusw.sbb.checkin.domain.push.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByUserId(Long userId);
    Optional<PushSubscription> findByEndpoint(String endpoint);
    // 소유권 결합 삭제 — 남의 구독을 endpoint 만으로 지우지 못하게 (IDOR 방지)
    void deleteByUserIdAndEndpoint(Long userId, String endpoint);
}
