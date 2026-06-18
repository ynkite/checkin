package idusw.sbb.triplinker.domain.post.entity;

import idusw.sbb.triplinker.domain.place.entity.Place;
import idusw.sbb.triplinker.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "PLACE_REVIEWS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PlaceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int rating;

    @Column(length = 500)
    private String comment;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PlaceReview(Place place, Post post, User user, int rating, String comment) {
        this.place   = place;
        this.post    = post;
        this.user    = user;
        this.rating  = rating;
        this.comment = comment;
    }

    public void update(int rating, String comment) {
        this.rating  = Math.max(1, Math.min(5, rating));
        this.comment = comment != null ? comment.trim() : null;
    }
}