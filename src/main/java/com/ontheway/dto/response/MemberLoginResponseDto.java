package com.ontheway.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberLoginResponseDto {
    @Schema(description = "액세스 토큰")
    private String accessToken;
    @Schema(description = "로그인 시간")
    private LocalDateTime createdAt;
}
