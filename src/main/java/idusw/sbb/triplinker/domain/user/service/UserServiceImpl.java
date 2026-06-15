package idusw.sbb.triplinker.domain.user.service;

import idusw.sbb.triplinker.domain.expense.dto.BudgetReportResponseDto;
import idusw.sbb.triplinker.domain.expense.entity.Expense;
import idusw.sbb.triplinker.domain.expense.repository.ExpenseRepository;
import idusw.sbb.triplinker.domain.plan.dto.TripListResponseDto;
import idusw.sbb.triplinker.domain.plan.entity.TravelPlan;
import idusw.sbb.triplinker.domain.plan.repository.TravelPlanRepository;
import idusw.sbb.triplinker.domain.user.dto.ScrapResponseDto;
import idusw.sbb.triplinker.domain.auth.repository.OAuthAccountRepository;
import idusw.sbb.triplinker.domain.auth.repository.RefreshTokenRepository;
import idusw.sbb.triplinker.domain.user.dto.UserNicknameUpdateRequest;
import idusw.sbb.triplinker.domain.user.dto.UserInfoResponseDto;
import idusw.sbb.triplinker.domain.user.entity.SecurityEventType;
import idusw.sbb.triplinker.domain.user.entity.User;
import idusw.sbb.triplinker.domain.user.entity.UserSecurityHistory;
import idusw.sbb.triplinker.domain.user.repository.ScrapRepository;
import idusw.sbb.triplinker.domain.user.repository.UserRepository;
import idusw.sbb.triplinker.domain.user.repository.UserSecurityHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
/**
 * 회원 관리(프로필/인증) 및 마이페이지 통합 데이터(가계부/여행/스크랩) 처리 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService { // 인터페이스 구현

    private final UserRepository userRepository;
    private final UserSecurityHistoryRepository historyRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final ExpenseRepository expenseRepository;
    private final ScrapRepository scrapRepository;

    //회원 프로필 조회
    @Override
    public UserInfoResponseDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));

        boolean isSocial = oAuthAccountRepository.findByUserId(userId).isPresent();

        return new UserInfoResponseDto(user, isSocial);
    }

    //회원 닉네임 변경 (쓰기 작업이므로 @Transactional 명시)
    @Override
    @Transactional
    public void updateNickname(Long userId, UserNicknameUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));
        user.updateNickname(request.getName()); // 엔티티 내부 변경 감지(Dirty Checking) 발동
    }

    //회원 탈퇴 (개인정보 마스킹 + 토큰/소셜 레코드 즉시 삭제)
    @Override
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));
        user.withdrawMasked(userId);
        refreshTokenRepository.deleteByUserId(userId);
        oAuthAccountRepository.deleteByUserId(userId);
    }

    //비밀번호 확인
    @Override
    public boolean verifyPassword(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    //회원 프로필 수정
    @Override
    @Transactional
    public void updateProfile(Long userId, String name, String region, String gender, LocalDate birthDate, String mbti) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));
        user.updateProfile(name, region, gender, birthDate, mbti);
    }

    //비밀번호 변경
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

    //로그인 실패 기록
    @Override
    @Transactional
    public void loginFailed(String username, String ipAddress) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. Username: " + username));

        user.increaseLoginFailCount(); // User 엔티티 메서드 호출

        UserSecurityHistory history = UserSecurityHistory.of(
                user,
                SecurityEventType.LOGIN_FAIL,
                "로그인 실패 IP: " + ipAddress
        );
        historyRepository.save(history); // UserServiceImpl에서는 이 이름이 맞습니다.
    }

    //가계부 조회 (마이페이지용 — 사용자 전체 실제 지출 목록)
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

    //장소 스크랩 조회
    @Override
    public Page<ScrapResponseDto> getMyScraps(Long userId, String category, Pageable pageable) {
        //프론트엔드가 쿼리스트링으로 보낸 종류 파라미터(HOTEL, CAFE 등)를 데이터에 맞게 가공 호출
        String refinedCategory = convertToDbCategory(category);

        return scrapRepository.findByUserIdAndCategory(userId, refinedCategory, pageable)
                .map(scrap -> ScrapResponseDto.builder()
                        .scrapId(scrap.getId())
                        .placeId(scrap.getPlaceId())
                        .category(scrap.getCategory())
                        .build());
    }

    //내 여행 기록 조회
    @Override
    public Page<TripListResponseDto> getMyTrips(Long userId, String condition, Pageable pageable) {
        LocalDate now = LocalDate.now();

        Page<TravelPlan> trips;

        if ("PAST".equalsIgnoreCase(condition)) {
            trips = travelPlanRepository.findPastTrips(userId, now, pageable);
        } else {
            trips = travelPlanRepository.findUpcomingTrips(userId, now, pageable);
        }

        return trips.map(trip -> TripListResponseDto.builder()
                .id(trip.getId())
                .title(trip.getTitle())
                .meta(trip.getStartDate() + " ~ " + trip.getEndDate())
                .budget(null)
                .status(trip.getEndDate().isBefore(now) ? "PAST" : "UPCOMING")
                .build());
    }

    //스크랩 카테고리 변환
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