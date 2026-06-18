package idusw.sbb.triplinker.domain.user.service;

import idusw.sbb.triplinker.domain.expense.dto.BudgetReportResponseDto;
import idusw.sbb.triplinker.domain.plan.dto.TripListResponseDto;
import idusw.sbb.triplinker.domain.user.dto.ScrapResponseDto;
import idusw.sbb.triplinker.domain.user.dto.UserNicknameUpdateRequest;
import idusw.sbb.triplinker.domain.user.dto.UserInfoResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;


//  회원 관리 기능의 비즈니스 핵심 명세를 정의한 서비스 인터페이스
//  약속된 기능(조회, 수정, 탈퇴)을 명시하여 느슨한 결합 구조를 만듬

public interface UserService {
    UserInfoResponseDto getProfile(Long userId);
    void updateNickname(Long userId, UserNicknameUpdateRequest request);
    void withdraw(Long userId);
    void loginFailed(String username, String ipAddress);

    boolean verifyPassword(Long userId, String rawPassword);
    void updateProfile(Long userId, String name, String region, String gender, LocalDate birthDate, String mbti);
    void updatePassword(Long userId, String currentRaw, String newRaw);

    Page<TripListResponseDto> getMyTrips(Long userId, String status, Pageable pageable);
    BudgetReportResponseDto getMyExpenseReport(Long userId, String category);
    Page<ScrapResponseDto> getMyScraps(Long userId, String category, Pageable pageable);
    java.util.List<ScrapResponseDto> getMyScrapsAll(Long userId);
    void addPlaceScrap(Long userId, Long placeId, String category);
    void deletePlaceScrap(Long userId, Long scrapId);
}