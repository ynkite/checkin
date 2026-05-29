package idusw.sbb.triplinker.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

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

    @Column(nullable = false, unique = true, length = 20)
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

    @Builder
    public User(String username, String passwordHash, String name, String email, String region, LocalDate birthDate, String gender, String mbti, String role, String status) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.name = name;
        this.email = email;
        this.region = region;
        this.birthDate = birthDate;
        this.gender = gender;
        this.mbti = mbti;
        // 값이 없을 경우 기본값으로 USER 권한과 ACTIVE(활성화) 상태를 부여합니다.
        this.role = (role != null) ? role : "USER"; // 기본값 설정
        this.status = (status != null) ? status : "ACTIVE"; // 기본값 설정
    }

    // User.java 클래스 내부에 추가할 메서드들 (맨 밑에 붙여넣기)

    // 1. 닉네임(name) 변경 도메인 로직 (공백 및 null 예외 검증 포함)
    public void updateNickname(String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("올바른 닉네임을 입력해주세요.");
        }
        this.name = newName;
    }

    // 2. 회원 안전 논리 탈퇴 로직 (DELETED로 변경)
    public void withdraw() {
        this.status = "DELETED";
    }

    // 비밀번호 수정
    public void updatePassword(String newPassword) {
        this.passwordHash = newPassword;
    }
}
