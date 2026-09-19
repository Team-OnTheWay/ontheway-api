package com.ontheway.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class MemberLoginRequestDto {
    @Schema(description = "회원 번호")
    private Long userNo;
    @Schema(description = "회원 아이디")
    private String accountId;
    @Schema(description = "회원 비밀번호")
    private String password;
    @Schema(description = "액세스 토큰")
    private String accessToken;
    @Schema(description = "리프레시 토큰")
    private String refreshToken;
}
