package idusw.sbb.checkin.domain.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.push.entity.PushSubscription;
import idusw.sbb.checkin.domain.push.repository.PushSubscriptionRepository;
import idusw.sbb.checkin.domain.push.service.PushNotificationService;
import idusw.sbb.checkin.domain.user.entity.User;
import idusw.sbb.checkin.domain.user.repository.UserRepository;
import nl.martijndwars.webpush.PushService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 같은 기기 재구독. 지우고 다시 넣으면 flush 때 INSERT 가 먼저 나가 uq_push_endpoint 에 걸린다 → 행을 고쳐 써야 한다 */
class PushResubscribeTest {

    private final PushSubscriptionRepository repo = mock(PushSubscriptionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PushNotificationService service =
            new PushNotificationService(repo, users, mock(PushService.class), new ObjectMapper());

    @Test
    void 같은_endpoint_를_다시_구독하면_행을_지우지_않고_주인과_키만_바꾼다() {
        User before = mock(User.class), after = mock(User.class);
        PushSubscription saved = PushSubscription.builder().user(before).endpoint("e1").p256dh("k1").auth("s1").build();
        when(users.findById(2L)).thenReturn(Optional.of(after));
        when(repo.findByEndpoint("e1")).thenReturn(Optional.of(saved));

        service.subscribe(2L, "e1", "k2", "s2");

        verify(repo, never()).delete(any());
        verify(repo, never()).save(any());
        assertThat(saved.getUser()).isSameAs(after);
        assertThat(saved.getP256dh()).isEqualTo("k2");
        assertThat(saved.getAuth()).isEqualTo("s2");
    }

    @Test
    void 처음_구독하는_endpoint_는_새로_저장한다() {
        when(users.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(repo.findByEndpoint("e9")).thenReturn(Optional.empty());

        service.subscribe(1L, "e9", "k", "s");

        verify(repo).save(any(PushSubscription.class));
    }
}
