package idusw.sbb.triplinker.domain.auth.repository;

import idusw.sbb.triplinker.domain.auth.entity.OAuthAccount;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

    Optional<OAuthAccount> findByProviderAndProviderId(String provider, String providerId);
    Optional<OAuthAccount> findByUserId(Long userId);
    boolean existsByUserId(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
