package idusw.sbb.triplinker.domain.user.service;

import idusw.sbb.triplinker.domain.auth.entity.OAuthAccount;
import idusw.sbb.triplinker.domain.auth.repository.OAuthAccountRepository;
import idusw.sbb.triplinker.domain.auth.repository.RefreshTokenRepository;
import idusw.sbb.triplinker.domain.expense.dto.BudgetReportResponseDto;
import idusw.sbb.triplinker.domain.expense.entity.Expense;
import idusw.sbb.triplinker.domain.expense.repository.ExpenseRepository;
import idusw.sbb.triplinker.domain.place.entity.Place;
import idusw.sbb.triplinker.domain.place.repository.PlaceRepository;
import idusw.sbb.triplinker.domain.post.repository.PlaceReviewRepository;
import idusw.sbb.triplinker.domain.plan.dto.TripListResponseDto;
import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.triplinker.domain.user.dto.ScrapResponseDto;
import idusw.sbb.triplinker.domain.user.entity.Scrap;
import idusw.sbb.triplinker.domain.user.dto.UserInfoResponseDto;
import idusw.sbb.triplinker.domain.user.dto.UserNicknameUpdateRequest;
import idusw.sbb.triplinker.domain.user.entity.SecurityEventType;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.entity.UserSecurityHistory;
import idusw.sbb.triplinker.domain.user.repository.ScrapRepository;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import idusw.sbb.triplinker.domain.user.repository.UserSecurityHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Value("${kakao.admin-key:}")
    private String kakaoAdminKey;

    private final UserRepository userRepository;
    private final UserSecurityHistoryRepository historyRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final ExpenseRepository expenseRepository;
    private final ScrapRepository scrapRepository;
    private final PlaceRepository placeRepository;
    private final PlaceReviewRepository placeReviewRepository;

    @Override
    public UserInfoResponseDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));
        boolean isSocial = oAuthAccountRepository.findByUserId(userId).isPresent();
        return new UserInfoResponseDto(user, isSocial);
    }

    @Override
    @Transactional
    public void updateNickname(Long userId, UserNicknameUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));
        user.updateNickname(request.getName());
    }

    @Override
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));

        // 소셜 계정이면 연결 해제 먼저 (DB 삭제 전에 providerId/accessToken 필요)
        oAuthAccountRepository.findByUserId(userId)
                .ifPresent(this::revokeSocialAccount);

        user.withdrawMasked(userId);
        refreshTokenRepository.deleteByUserId(userId);
        oAuthAccountRepository.deleteByUserId(userId);
    }

    private void revokeSocialAccount(OAuthAccount oauthAccount) {
        try {
            RestClient restClient = RestClient.create();

            if ("kakao".equals(oauthAccount.getProvider())) {
                restClient.post()
                        .uri("https://kapi.kakao.com/v1/user/unlink")
                        .header("Authorization", "KakaoAK " + kakaoAdminKey)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body("target_id_type=user_id&target_id=" + oauthAccount.getProviderId())
                        .retrieve()
                        .toBodilessEntity();
                log.info("[카카오 연결 해제 완료] providerId={}", oauthAccount.getProviderId());

            } else if ("google".equals(oauthAccount.getProvider())
                    && oauthAccount.getAccessToken() != null) {
                restClient.post()
                        .uri("https://oauth2.googleapis.com/revoke?token=" + oauthAccount.getAccessToken())
                        .retrieve()
                        .toBodilessEntity();
                log.info("[구글 연결 해제 완료] userId={}", oauthAccount.getUserId());
            }

        } catch (Exception e) {
            log.warn("[소셜 연결 해제 실패 — 탈퇴는 계속 진행] provider={}, userId={}, reason={}",
                    oauthAccount.getProvider(), oauthAccount.getUserId(), e.getMessage());
        }
    }

    @Override
    public boolean verifyPassword(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, String name, String region, String gender, LocalDate birthDate, String mbti) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));
        user.updateProfile(name, region, gender, birthDate, mbti);
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, String currentRaw, String newRaw) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));
        if (!passwordEncoder.matches(currentRaw, user.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        user.updatePassword(passwordEncoder.encode(newRaw));
    }

    @Override
    @Transactional
    public void loginFailed(String username, String ipAddress) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. Username: " + username));
        user.increaseLoginFailCount();
        UserSecurityHistory history = UserSecurityHistory.of(
                user,
                SecurityEventType.LOGIN_FAIL,
                "로그인 실패 IP: " + ipAddress
        );
        historyRepository.save(history);
    }

    @Override
    public BudgetReportResponseDto getMyExpenseReport(Long userId, String category) {
        String dbCategory = category == null ? null : category.toUpperCase();
        List<Expense> expenseList = dbCategory == null
                ? expenseRepository.findByUserId(userId)
                : expenseRepository.findByUserIdAndCategory(userId, dbCategory);
        long totalAct = expenseList.stream()
                .filter(e -> !e.isEstimated())
                .mapToLong(Expense::getAmount)
                .sum();
        List<BudgetReportResponseDto.ExpenseDetailDto> actualDetails = expenseList.stream()
                .filter(e -> !e.isEstimated())
                .map(exp -> BudgetReportResponseDto.ExpenseDetailDto.builder()
                        .id(exp.getId())
                        .category(exp.getCategory())
                        .description(exp.getDescription())
                        .amount(exp.getAmount())
                        .date(exp.getExpenseDate().toString())
                        .build())
                .collect(Collectors.toList());
        return BudgetReportResponseDto.builder()
                .totalActualAmount(totalAct)
                .actualExpenses(actualDetails)
                .build();
    }

    @Override
    public Page<ScrapResponseDto> getMyScraps(Long userId, String category, Pageable pageable) {
        String refinedCategory = convertToDbCategory(category);
        return scrapRepository.findByUserIdAndCategory(userId, refinedCategory, pageable)
                .map(scrap -> ScrapResponseDto.builder()
                        .scrapId(scrap.getId())
                        .placeId(scrap.getPlaceId())
                        .category(scrap.getCategory())
                        .build());
    }

    @Override
    public List<ScrapResponseDto> getMyScrapsAll(Long userId) {
        return scrapRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(scrap -> {
                    Place place = placeRepository.findById(scrap.getPlaceId()).orElse(null);
                    double avg = placeReviewRepository.findByPlace_IdOrderByCreatedAtDesc(scrap.getPlaceId())
                            .stream()
                            .mapToInt(r -> r.getRating())
                            .average()
                            .orElse(0.0);
                    return ScrapResponseDto.builder()
                            .scrapId(scrap.getId())
                            .placeId(scrap.getPlaceId())
                            .category(scrap.getCategory())
                            .placeName(place != null ? place.getName() : "알 수 없는 장소")
                            .address(place != null ? place.getAddress() : null)
                            .avgRating(avg > 0 ? Math.round(avg * 10.0) / 10.0 : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean togglePlaceScrap(Long userId, Long placeId, String category) {
        return scrapRepository.findByUserIdAndPlaceId(userId, placeId)
                .map(scrap -> {
                    scrapRepository.delete(scrap);
                    return false; // 스크랩 취소됨
                })
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
                    placeRepository.findById(placeId)
                            .orElseThrow(() -> new IllegalArgumentException("장소를 찾을 수 없습니다."));
                    scrapRepository.save(Scrap.builder()
                            .user(user)
                            .placeId(placeId)
                            .category(category)
                            .build());
                    return true; // 스크랩 등록됨
                });
    }

    @Override
    @Transactional
    public void deletePlaceScrap(Long userId, Long scrapId) {
        Scrap scrap = scrapRepository.findById(scrapId)
                .orElseThrow(() -> new IllegalArgumentException("스크랩을 찾을 수 없습니다."));
        if (!scrap.getUser().getId().equals(userId)) {
            throw new IllegalStateException("스크랩 삭제 권한이 없습니다.");
        }
        scrapRepository.deleteById(scrapId);
    }


    @Override
    public Page<TripListResponseDto> getMyTrips(Long userId, String condition, Pageable pageable) {
        LocalDate now = LocalDate.now();
        Page<TravelPlan> trips = "PAST".equalsIgnoreCase(condition)
                ? travelPlanRepository.findPastTrips(userId, now, pageable)
                : travelPlanRepository.findUpcomingTrips(userId, now, pageable);
        return trips.map(trip -> TripListResponseDto.builder()
                .id(trip.getId())
                .title(trip.getTitle())
                .meta(trip.getStartDate() + " ~ " + trip.getEndDate())
                .budget(null)
                .status(trip.getEndDate().isBefore(now) ? "PAST" : "UPCOMING")
                .build());
    }

    private String convertToDbCategory(String type) {
        if (type == null) return "STAY";
        return switch (type.toUpperCase()) {
            case "HOTEL", "STAY" -> "STAY";
            case "RESTAURANT", "FOOD" -> "FOOD";
            case "TOUR" -> "TOUR";
            case "CAFE" -> "CAFE";
            default -> type.toUpperCase();
        };
    }
}