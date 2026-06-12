package idusw.sbb.triplinker.domain.post.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "POST_IMAGES")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소속 게시글 (다대일 연관관계)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "s3_object_key", nullable = false, length = 500)
    private String s3ObjectKey;

    @Column(name = "image_order")
    private Integer imageOrder;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PostImage(Post post, String imageUrl, String s3ObjectKey, Integer imageOrder) {
        this.post = post;
        this.imageUrl = imageUrl;
        this.s3ObjectKey = s3ObjectKey;
        this.imageOrder = imageOrder;
    }
}

