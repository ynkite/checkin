package idusw.sbb.triplinker.domain.user.dto;

import idusw.sbb.triplinker.domain.user.entity.User;
import lombok.Getter;
import java.time.LocalDate;

/**
 * 회원 프로필 조회 요청 시 클라이언트에게 안전하게 데이터를 응답하기 위한 DTO입니다.
 * User 엔티티에서 필요한 정보(비밀번호 제외)만 추출하여 바인딩합니다.
 */
@Getter
public class UserInfoResponseDto {
    private Long id;
    private String username;
    private String name;
    private String email;
    private LocalDate birthDate;
    private String gender;
    private String mbti;
    private String region;
    private String status;

    // 엔티티 객체를 DTO 객체로 변환하는 생성자
    public UserInfoResponseDto(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.name = user.getName();
        this.email = user.getEmail();
        this.birthDate = user.getBirthDate();
        this.gender = user.getGender();
        this.mbti = user.getMbti();
        this.region = user.getRegion();
        this.status = user.getStatus();
    }
}