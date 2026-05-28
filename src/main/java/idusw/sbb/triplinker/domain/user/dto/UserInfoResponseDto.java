package idusw.sbb.triplinker.domain.user.dto;

import idusw.sbb.triplinker.domain.user.entity.User;
import lombok.Getter;
import java.time.LocalDate;

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