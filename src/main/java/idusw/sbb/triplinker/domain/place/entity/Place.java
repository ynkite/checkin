package idusw.sbb.triplinker.domain.place.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "PLACES")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceCategory category;

    @Column(length = 300)
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    // false=0, true=1
    @Column(name = "is_indoor", nullable = false)
    @Builder.Default
    private boolean isIndoor = false;

    @Column(name = "is_no_kids", nullable = false)
    @Builder.Default
    private boolean isNoKids = false;

    @Column(name = "is_pet_friendly", nullable = false)
    @Builder.Default
    private boolean isPetFriendly = false;

    @Column(name = "avg_price")
    private Integer avgPrice;

    @Column(name = "saved_count")
    private Integer savedCount;

    // 별점 3.0 미만은 추천 후보 자동 제외 대상
    @Column(name = "external_rating", precision = 3, scale = 1)
    private BigDecimal externalRating;

    @Column(name = "external_link", length = 500)
    private String externalLink;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}