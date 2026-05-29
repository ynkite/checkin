package idusw.sbb.triplinker.domain.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 이메일 인증번호를 임시로 저장하는 테이블
 * 사용자가 인증번호를 요청하면 이 테이블에 발송된 번호와 만료 시간을 기록
 */
@Entity
@Table(name = "email_auth")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 기본 생성자 접근을 막아 무분별한 객체 생성을 방지합니다.
public class EmailAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 인증을 요청한 사용자의 이메일 주소
    @Column(nullable = false, length = 100)
    private String email;

    // 이메일로 발송한 6자리 난수
    @Column(nullable = false, length = 6)
    private String authCode;

    // 인증번호 만료 시간 (발송 시점으로부터 3분 뒤)
    @Column(nullable = false)
    private LocalDateTime expiryDate;

    @Builder
    public EmailAuth(String email, String authCode, LocalDateTime expiryDate) {
        this.email = email;
        this.authCode = authCode;
        this.expiryDate = expiryDate;
    }
}