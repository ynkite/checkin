package idusw.sbb.checkin.domain.planshare.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TripMemberResponseDto {
    private String name;
    private String email;
    private String role;
}