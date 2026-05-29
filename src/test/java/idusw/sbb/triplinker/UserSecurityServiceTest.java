package idusw.sbb.triplinker;

import idusw.sbb.triplinker.domain.user.entity.SecurityEventType;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.entity.UserSecurityHistory;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import idusw.sbb.triplinker.domain.user.repository.UserSecurityHistoryRepository;
import idusw.sbb.triplinker.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional // 테스트가 끝나면 DB 데이터를 자동으로 롤백해줍니다.
public class UserSecurityServiceTest {

    @Autowired
    private UserService userService; // 상연님이 구현하신 UserServiceImpl이 주입됩니다.

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSecurityHistoryRepository historyRepository;

    private final String TARGET_USERNAME = "sangyeon123";

    @BeforeEach
    void setUp() {
        // 테스트용 유저 데이터 사전에 세팅 (빌더에 없는 failedAttempts 제거)
        User user = User.builder()
                .username(TARGET_USERNAME)
                .passwordHash("hashedPassword123!")
                .name("정상연")
                .email("sangyeon@triplinker.com")
                .region("서울")
                .status("ACTIVE")       // 초기 상태는 ACTIVE
                .role("USER")
                .build();
        userRepository.save(user);
    }

    @Test
    @DisplayName("로그인 5회 실패 시 5분간 계정이 잠기고, 이력 테이블에 데이터가 누적된다.")
    void loginFailed_FiveTimes_ShouldLockAccount() {
        // Given: 테스트용 가상 IP
        String clientIp = "127.0.0.1";

        // When: 상연님이 만든 loginFailed 메서드를 연속으로 5번 호출 (5회 실패 모사)
        for (int i = 1; i <= 5; i++) {
            userService.loginFailed(TARGET_USERNAME, clientIp);
        }

        // Then 1: 유저 정보 검증 (실패 횟수가 5가 되었고 lockedUntil이 설정되었는지)
        User updatedUser = userRepository.findByUsername(TARGET_USERNAME)
                .orElseThrow(() -> new AssertionError("유저를 찾을 수 없습니다."));

        // failedAttempts -> loginFailCount 로 변경됨
        assertEquals(5, updatedUser.getLoginFailCount(), "로그인 실패 횟수는 5회여야 합니다.");

        // 상태 변경(SUSPENDED) 대신 isLocked() 메서드와 lockedUntil 값으로 잠금 검증
        assertTrue(updatedUser.isLocked(), "5회 실패 시 isLocked()가 true를 반환해야 합니다.");
        assertNotNull(updatedUser.getLockedUntil(), "계정 잠금 해제 시간(lockedUntil)이 설정되어야 합니다.");

// Then 2: UserSecurityHistory 이력 테이블 검증
        List<UserSecurityHistory> historyList = historyRepository.findAll();
        assertTrue(historyList.size() >= 5, "최소 5개 이상의 보안 이력이 저장되어야 합니다.");

        // 가장 최근에 쌓인 이력의 값 확인
        UserSecurityHistory latestHistory = historyList.get(historyList.size() - 1);

        // 💡 getActionType() -> getEventType() 이름 변경 및 String("LOGIN_FAIL") 비교에서 Enum 비교로 변경
        assertEquals(SecurityEventType.LOGIN_FAIL, latestHistory.getEventType(), "이력 타입이 LOGIN_FAIL이어야 합니다.");

        // 💡 getIpAddress() -> getIpAddress 필드가 없으므로, description 문자열 포함 여부로 IP 검증 (또는 엔티티에 ip 필드를 추가하세요)
        assertTrue(latestHistory.getDescription().contains(clientIp), "기록된 설명(description)에 IP가 포함되어야 합니다.");
    }
}