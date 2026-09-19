package com.ontheway.dto.request;

import com.ontheway.enums.EmailPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EmailAuthRequestDto {
    @Schema(description = "이메일")
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @Schema(description = "이메일 인증 용도")
    @NotNull(message = "인증 용도는 필수입니다.")
    private EmailPurpose purpose;
}
