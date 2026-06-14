package idusw.sbb.triplinker.domain.planshare.dto;

import lombok.Data;

@Data
public class ShareInviteRequestDto {
    private String email;
    private String role;
}