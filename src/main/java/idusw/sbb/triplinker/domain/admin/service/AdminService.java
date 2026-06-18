package idusw.sbb.triplinker.domain.admin.service;

import idusw.sbb.triplinker.domain.admin.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

// 관리자 - 회원 관리 기능의 비즈니스 명세를 정의한 서비스 인터페이스
public interface AdminService {

    Page<AdminUserListResponseDto> getUsers(String status, Pageable pageable);
    void suspendUser(Long adminId, Long targetUserId, SuspendRequestDto dto);
    void unsuspendUser(Long adminId, Long targetUserId);
    void promoteToAdmin(Long adminId, Long targetUserId);
    void demoteToUser(Long adminId, Long targetUserId);

    AdminDashboardResponseDto getDashboard();
    AdminStatisticsResponseDto getStatistics(String startDate, String endDate);

    Page<CurationResponseDto> getCurations(Pageable pageable);
    CurationResponseDto getCuration(Long curationId);
    Long createCuration(Long adminId, CurationRequestDto dto);
    void updateCuration(Long adminId, Long curationId, CurationRequestDto dto);
    void deleteCuration(Long adminId, Long curationId);
    List<CurationResponseDto> getPublicCurations();
}