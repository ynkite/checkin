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
 * UserService 인터페이스를 구현하여 회원 도메인의 실제 서비스 로직을 처리하는 구현체입니다.
 * - 회원 프로필 조회, 수정, 탈퇴, 비밀번호 변경 기능을 처리한다.
 * - 마이페이지에서 사용하는 내 여행 기록, 가계부, 장소 스크랩 조회 기능을 제공한다.
 * - Repository를 통해 DB 데이터를 조회하고 DTO 형태로 변환한다.
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
    // 추후 소셜 로그인 연동 기능 merge 시 재활성화 예정
    // private final OAuthAccountRepository oAuthAccountRepository;
    // private final RefreshTokenRepository refreshTokenRepository;

    // 💡 [필수 고침] 누락되었던 3가지 레포지토리 의존성을 명확하게 추가합니다.
    private final TravelPlanRepository travelPlanRepository;
    private final ExpenseRepository expenseRepository;
    private final ScrapRepository scrapRepository;

    // 1. 회원 프로필 조회 로직
    @Override
    public UserInfoResponseDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));

        boolean isSocial = oAuthAccountRepository.findByUserId(userId).isPresent();

        return new UserInfoResponseDto(user, isSocial);
    }

    // 2. 회원 닉네임 변경 비즈니스 로직 (쓰기 작업이므로 @Transactional 명시)
    @Override
    @Transactional
    public void updateNickname(Long userId, UserNicknameUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));
        user.updateNickname(request.getName()); // 엔티티 내부 변경 감지(Dirty Checking) 발동
    }

    // 3. 회원 탈퇴 - 개인정보 마스킹 + 토큰/소셜 레코드 즉시 삭제
    @Override
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다. ID: " + userId));
        user.withdrawMasked(userId);
        refreshTokenRepository.deleteByUserId(userId);
        oAuthAccountRepository.deleteByUserId(userId);
    }

    //4. 비밀번호 확인
    @Override
    public boolean verifyPassword(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    //5. 회원 프로필 수정
    @Override
    @Transactional
    public void updateProfile(Long userId, String name, String region, String gender, LocalDate birthDate, String mbti) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다."));
        user.updateProfile(name, region, gender, birthDate, mbti);
    }

    //6. 비밀번호 변경
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

    //7. 로그인 실패 기록
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

    // 8. [마이페이지] 가계부 조회
    // - category가 있으면 해당 카테고리만 조회하고, 없으면 전체 지출 내역을 조회한다.
    // - 지출 금액을 합산하여 총액과 상세 내역 DTO로 반환한다..
    @Override
    public BudgetReportResponseDto getMyExpenseReport(Long userId, String category) {
        // category가 없으면 전체 가계부 조회, 있으면 해당 카테고리만 조회
        String dbCategory = category == null ? null : category.toUpperCase();

        List<Expense> expenseList = dbCategory == null
                ? expenseRepository.findByUserId(userId)
                : expenseRepository.findByUserIdAndCategory(userId, dbCategory);

        // 지출 금액(Long)을 모두 합산하여 총 지출 금액을 계산한다.
        Long total = expenseList.stream()
                .mapToLong(Expense::getAmount)
                .sum();

        List<BudgetReportResponseDto.ExpenseDetailDto> details = expenseList.stream()
                .map(exp -> BudgetReportResponseDto.ExpenseDetailDto.builder()
                        .id(exp.getId())
                        .amount(exp.getAmount()) // 지출 금액 매핑
                        .memo(exp.getMemo())
                        .date(exp.getExpenseDate().toString())
                        .build())
                .collect(Collectors.toList());

        return BudgetReportResponseDto.builder()
                .currentPageCategory(dbCategory)
                .categoryTotalAmount(total)
                .expenses(details)
                .build();
    }

    // 9. [마이페이지] 장소 스크랩 조회
    // - 로그인한 사용자가 스크랩한 장소 목록을 카테고리별로 조회한다.
    // - 프론트 요청값을 DB 카테고리 값으로 변환한다.
    // - 조회 결과를 페이징 처리하여 ScrapResponseDto로 반환한다.
    @Override
    public Page<ScrapResponseDto> getMyScraps(Long userId, String category, Pageable pageable) {
        // 프론트엔드가 쿼리스트링으로 보낸 종류 파라미터(HOTEL, CAFE 등)를 데이터에 맞게 가공 호출
        String refinedCategory = convertToDbCategory(category);

        return scrapRepository.findByUserIdAndCategory(userId, refinedCategory, pageable)
                .map(scrap -> ScrapResponseDto.builder()
                        .scrapId(scrap.getId())
                        .placeId(scrap.getPlaceId())
                        .category(scrap.getCategory())
                        .build());
    }

    // 10. [마이페이지] 내 여행 기록 조회
    // - condition 값이 PAST이면 지난 여행을 조회한다.
    // - 그 외에는 다가오는 여행을 조회한다.
    // - 조회된 TravelPlan 엔티티를 TripListResponseDto로 변환한다.
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

    // 11. 스크랩 카테고리 변환
    // - 프론트에서 전달되는 카테고리명을 DB 저장값에 맞게 변환한다.
    private String convertToDbCategory(String type) {
        if (type == null) return "STAY";
        switch (type.toUpperCase()) {
            case "HOTEL": case "STAY": return "STAY";
            case "RESTAURANT": case "FOOD": return "FOOD";
            case "TOUR": return "TOUR";
            case "CAFE": return "CAFE";
            default: return type.toUpperCase();
        }
    }
}