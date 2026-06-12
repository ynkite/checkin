package idusw.sbb.triplinker.config;

import idusw.sbb.triplinker.domain.post.entity.Post;
import idusw.sbb.triplinker.domain.post.entity.PostLike;
import idusw.sbb.triplinker.domain.post.repository.PostLikeRepository;
import idusw.sbb.triplinker.domain.post.repository.PostRepository;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("local")
@RequiredArgsConstructor
public class DummyDataInit implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {

        //"test"라는 아이디가 DB에 없을 때만
        if (userRepository.findByUsername("posttest").isEmpty()) {

            User dummyUser = User.builder()
                    .username("posttest")
                    .passwordHash(passwordEncoder.encode("Asdf1234!"))
                    .name("김테스트")
                    .email("test@test")
                    .region("서울")
                    .role("USER")
                    .status("ACTIVE")
                    .lastPwChangedAt(LocalDateTime.now())
                    .build();

            userRepository.save(dummyUser);

            Post post1 = Post.builder()
                    .user(dummyUser)
                    .title("제주도 3박 4일 가성비 힐링 코스")
                    .content("정말 재밌는 여행이었습니다. 강력 추천!")
                    .styleTags("[\"가성비\", \"힐링\"]")
                    .isPublic(true)
                    .build();
            post1.increaseViewCount(); // 조회수 1
            post1.increaseLikeCount(); // 좋아요 1

            Post post2 = Post.builder()
                    .user(dummyUser)
                    .title("부산 바다뷰 카페 투어 후기")
                    .content("바다 뷰가 끝내주네요.")
                    .styleTags("[\"바다뷰\", \"카페\"]")
                    .isPublic(true)
                    .build();

            postRepository.save(post1);
            postRepository.save(post2);

            PostLike dummyLike = PostLike.builder()
                    .user(dummyUser)
                    .post(post1)
                    .build();

            postLikeRepository.save(dummyLike);

            System.out.println("========테스트용 더미 유저 및 게시글 생성 완료=========");
        }
    }
}