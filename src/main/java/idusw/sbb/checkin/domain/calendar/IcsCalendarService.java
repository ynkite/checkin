package idusw.sbb.checkin.domain.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import idusw.sbb.checkin.domain.plan.entity.TravelPlan;
import idusw.sbb.checkin.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.checkin.domain.user.entity.User;
import idusw.sbb.checkin.domain.user.repository.UserRepository;
import idusw.sbb.checkin.global.util.ShareTokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// 캘린더 구독(.ics) — 사용자별 토큰 발급, 플랜 일정을 iCalendar 피드로 렌더.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IcsCalendarService {

    private final UserRepository userRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final ObjectMapper objectMapper;

    // 구독 토큰 확보 (없으면 발급). 마이페이지에서 URL 만들 때 쓴다.
    @Transactional
    public String getOrCreateToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
        if (user.getIcsToken() == null) {
            user.assignIcsToken(uniqueToken());
        }
        return user.getIcsToken();
    }

    // 재발급 — 기존 구독 링크를 끊고 새 토큰 (유출 대응)
    @Transactional
    public String regenerateToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
        user.assignIcsToken(uniqueToken());
        return user.getIcsToken();
    }

    // 폐기
    @Transactional
    public void revokeToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
        user.assignIcsToken(null);
    }

    // 토큰 → .ics 문자열 (인증 없이 열리는 진입점)
    public String renderIcs(String token) {
        User user = userRepository.findByIcsToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 폐기된 구독 링크입니다."));

        List<IcsFeedBuilder.CalEvent> events = new ArrayList<>();
        for (TravelPlan plan : travelPlanRepository.findByUserIdOrderByCreatedAtDesc(user.getId())) {
            collectEvents(plan, events);
        }
        return IcsFeedBuilder.build(user.getName() + "님의 체크인 여행 일정", events);
    }

    // routeJson([{day,places:[{name,time}]}]) 을 파싱해 이벤트로 변환
    private void collectEvents(TravelPlan plan, List<IcsFeedBuilder.CalEvent> out) {
        String json = plan.getRouteJson();
        if (json == null || json.isBlank() || plan.getStartDate() == null) return;
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) return;
            for (JsonNode dayNode : root) {
                int day = dayNode.path("day").asInt(1);
                LocalDate date = plan.getStartDate().plusDays(Math.max(0, day - 1));
                JsonNode places = dayNode.path("places");
                if (!places.isArray()) continue;
                int idx = 0;
                for (JsonNode place : places) {
                    String name = place.path("name").asText("").trim();
                    LocalTime time = parseTime(place.path("time").asText(""));
                    if (!name.isEmpty() && time != null) {
                        String uid = "plan" + plan.getId() + "-d" + day + "-" + idx;
                        out.add(new IcsFeedBuilder.CalEvent(uid, name, LocalDateTime.of(date, time)));
                    }
                    idx++;
                }
            }
        } catch (Exception e) {
            // 한 플랜이 깨져도 나머지 피드는 나가야 한다
            System.err.println("[Ics] 플랜 파싱 실패 (planId=" + plan.getId() + "): " + e.getMessage());
        }
    }

    // "HH:mm" → LocalTime, 형식이 아니면 null (시각 없는 항목은 이벤트에서 제외)
    private LocalTime parseTime(String s) {
        if (s == null || !s.matches("\\d{1,2}:\\d{2}")) return null;
        try {
            String[] p = s.split(":");
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            if (h > 23 || m > 59) return null;
            return LocalTime.of(h, m);
        } catch (Exception e) {
            return null;
        }
    }

    private String uniqueToken() {
        for (int i = 0; i < 5; i++) {
            String t = ShareTokenGenerator.generate();
            if (userRepository.findByIcsToken(t).isEmpty()) return t;
        }
        throw new IllegalStateException("토큰 생성 실패");
    }
}
