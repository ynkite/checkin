//장소 스크랩 엔티티
//사용자가 스크랩한 장소 정보를 PLACE_SCRAPS 테이블에 저장
//user_id와 place_id를 통해 사용자와 장소를 연결
//category 값으로 숙소, 맛집, 관광지, 카페를 구분

package idusw.sbb.triplinker.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "PLACE_SCRAPS",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_user_place",
                        columnNames = {"user_id", "place_id"}
                )
        }
)
public class Scrap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(nullable = false, length = 20)
    private String category; // STAY, FOOD, TOUR, CAFE

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}