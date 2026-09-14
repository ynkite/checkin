package idusw.sbb.checkin.domain.push.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.push.entity.PushSubscription;
import idusw.sbb.checkin.domain.push.repository.PushSubscriptionRepository;
import idusw.sbb.checkin.domain.user.entity.User;
import idusw.sbb.checkin.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import nl.martijndwars.webpush.Notification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

// 브라우저 푸시 구독 관리 + 전송. 만료된 구독(404/410)은 자동 정리한다.
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final nl.martijndwars.webpush.PushService pushService;
    private final ObjectMapper objectMapper;

    @Value("${push.vapid.public-key}")
    private String publicKey;

    // 프론트가 구독할 때 필요한 VAPID 공개키
    public String getPublicKey() {
        return publicKey;
    }

    // 구독 등록 (같은 endpoint 재구독은 갱신)
    @Transactional
    public void subscribe(Long userId, String endpoint, String p256dh, String auth) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
        // 같은 기기(endpoint) 재구독이면 기존 것 지우고 다시 저장 (키가 바뀌었을 수 있음)
        subscriptionRepository.findByEndpoint(endpoint)
                .ifPresent(subscriptionRepository::delete);
        subscriptionRepository.save(PushSubscription.builder()
                .user(user).endpoint(endpoint).p256dh(p256dh).auth(auth).build());
    }

    // 구독 해제
    @Transactional
    public void unsubscribe(String endpoint) {
        subscriptionRepository.deleteByEndpoint(endpoint);
    }

    // 특정 사용자의 모든 기기로 푸시 전송
    @Transactional
    public void sendToUser(Long userId, String title, String body, String url) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "title", title, "body", body, "url", url == null ? "/" : url));
        } catch (Exception e) {
            throw new IllegalStateException("푸시 페이로드 생성 실패", e);
        }

        for (PushSubscription sub : subscriptionRepository.findByUserId(userId)) {
            try {
                Notification notification = new Notification(
                        sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload.getBytes());
                int status = pushService.send(notification).getStatusLine().getStatusCode();
                // 404/410 = 구독 만료·폐기됨 → 정리
                if (status == 404 || status == 410) {
                    subscriptionRepository.delete(sub);
                }
            } catch (Exception e) {
                // 한 기기 실패가 다른 기기 전송을 막지 않는다
                System.err.println("[Push] 전송 실패 (subId=" + sub.getId() + "): " + e.getMessage());
            }
        }
    }
}
