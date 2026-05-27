package idusw.sbb.triplinker.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

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
        this.role = (role != null) ? role : "USER"; // 기본값 설정
        this.status = (status != null) ? status : "ACTIVE"; // 기본값 설정
    }

    // User.java 클래스 내부에 추가할 메서드들 (맨 밑에 붙여넣기)

    // 1. 닉네임(name) 변경 비즈니스 로직
    public void updateNickname(String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("올바른 닉네임을 입력해주세요.");
        }
        this.name = newName;
    }

    // 2. 회원 탈퇴 로직 (status를 INACTIVE로 변경)
    public void withdraw() {
        this.status = "INACTIVE";
    }
}
