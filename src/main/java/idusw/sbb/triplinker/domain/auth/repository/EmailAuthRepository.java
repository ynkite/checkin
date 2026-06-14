package idusw.sbb.triplinker.domain.auth.repository;

import idusw.sbb.triplinker.domain.auth.entity.EmailAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


//EmailAuth(이메일 인증) 테이블에 접근하여 데이터를 저장하고 찾는 역할
@Repository
public interface EmailAuthRepository extends JpaRepository<EmailAuth, Long> {
    // 특정 이메일로 발송된 가장 최근의 인증 정보를 찾아옴.

    // 나중에 사용자가 인증번호를 입력했을 때, 이 메서드로 DB에서 꺼내와서 정답을 비교
    Optional<EmailAuth> findTopByEmailOrderByExpiryDateDesc(String email);
}