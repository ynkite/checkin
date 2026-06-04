package idusw.sbb.triplinker.domain.auth.entity;


import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Entity
@Table(name = "oauth_accounts")
@Getter
@NoArgsConstructor
public class OAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    //소셜 종류 (kakao, google 등)
    private String provider;

    //고유 식별 번호
    private String providerId;

    private LocalDateTime createdAt;

    @Builder
    public OAuthAccount(Long userId, String provider, String providerId) {
        this.userId = userId;
        this.provider = provider;
        this.providerId = providerId;
        this.createdAt = LocalDateTime.now();
    }
}
