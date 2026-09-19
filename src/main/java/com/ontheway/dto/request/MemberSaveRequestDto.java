package com.ontheway.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MemberSaveRequestDto {
    @Schema(description = "회원 아이디")
    @NotBlank
    private String userId;

    @Schema(description = "회원 이름")
    @NotBlank
    private String userName;

    @Schema(description = "회원 생일")
    @NotBlank
    private String birthday;

    @Schema(description = "회원 이메일")
    @NotBlank
    private String email;

    @Schema(description = "회원 비밀번호")
    @NotBlank
    private String password;

    @Schema(description = "회원 닉네임")
    @NotBlank
    private String nickName;
}
