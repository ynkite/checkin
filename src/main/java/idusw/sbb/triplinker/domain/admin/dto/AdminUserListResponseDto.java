package idusw.sbb.triplinker.domain.admin.dto;

import idusw.sbb.triplinker.domain.user.entity.User;

import java.time.LocalDateTime;

public record AdminUserListResponseDto(
        Long userId,
        String username,
        String name,
        String email,
        String status,
        String role,
        LocalDateTime createdAt
) {
    public static AdminUserListResponseDto from(User user) {
        return new AdminUserListResponseDto(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getEmail(),
                user.getStatus(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}