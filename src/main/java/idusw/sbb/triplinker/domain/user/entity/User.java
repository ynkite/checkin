package idusw.sbb.triplinker.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 회원(users 테이블)의 정보와 도메인 핵심 비즈니스 로직을 담은 JPA 엔티티 클래스입니다.
 * 유저의 상태 정보 관리 및 닉네임 변경, 논리 탈퇴 로직을 내부에 포함합니다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 기본 생성자
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 기본키

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 1)
    private String gender;

    @Column(length = 4)
    private String mbti;

    @Column(nullable = false, length = 10)
    private String role; // USER, ADMIN

    @Column(nullable = false, length = 10)
    private String status; // ACTIVE, DELETED, SUSPENDED

    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount = 0;  // 연속 로그인 실패 횟수

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;  // 잠금 해제 시각

    @Column(name = "last_pw_changed_at")
    private LocalDateTime lastPwChangedAt;  // 마지막 비밀번호 변경 시각

    @Column(name = "pw_change_noti_at")
    private LocalDateTime pwChangeNotiAt;  // 90일 변경 권장 모달 노출 시각

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;  // 탈퇴 처리 시각

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public User(String username, String passwordHash, String name, String email, String region, LocalDate birthDate, String gender, String mbti, String role, String status, LocalDateTime lastPwChangedAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.name = name;
        this.email = email;
        this.region = region;
        this.birthDate = birthDate;
        this.gender = gender;
        this.mbti = mbti;
        this.role = (role != null) ? role : "USER"; // 기본값 설정
        this.status = (status != null) ? status : "ACTIVE"; // 기본값 설정
        this.lastPwChangedAt = lastPwChangedAt;
    }

    //닉네임 변경
    public void updateNickname(String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("올바른 닉네임을 입력해주세요.");
        }
        this.name = newName;
    }

    //프로필 전체 수정 (null이면 해당 필드 유지)
    public void updateProfile(String name, String region, String gender, LocalDate birthDate, String mbti) {
        if (name     != null && !name.isBlank())          this.name      = name;
        if (region   != null && !region.isBlank())        this.region    = region;
        if (gender   != null && !gender.isBlank())        this.gender    = gender;
        if (birthDate != null)                             this.birthDate = birthDate;
        if (mbti     != null && mbti.length() == 4)       this.mbti      = mbti;
    }

    //회원 탈퇴
    public void withdraw() {
        this.status = "DELETED";
        this.deletedAt = LocalDateTime.now();
    }

    //비밀번호 수정
    public void updatePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.lastPwChangedAt = LocalDateTime.now();
    }

    public boolean isLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    //로그인 실패 시 호출 → 5회 도달 시 5분 잠금
    public void increaseLoginFailCount() {
        this.loginFailCount++;
        if (this.loginFailCount >= 5) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(5);
        }
    }

    //로그인 성공 시 호출 → 실패 횟수 및 잠금 초기화
    public void resetLoginFail() {
        this.loginFailCount = 0;
        this.lockedUntil = null;
    }

    //90일 권장 모달 노출 시각 기록
    public void recordPwChangeNotified() {
        this.pwChangeNotiAt = LocalDateTime.now();
    }
}
