package com.ontheway.controller;

import com.ontheway.dto.request.EmailAuthRequestDto;
import com.ontheway.dto.request.EmailValidRequestDto;
import com.ontheway.dto.response.EmailAuthResponseDto;
import com.ontheway.dto.response.EmailValidResponseDto;
import com.ontheway.global.response.ApiResponse;
import com.ontheway.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/email")
@Tag(name = "이메일 인증")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/auth")
    @Operation(summary = "이메일 인증 요청")
    public ApiResponse<?> sendCode(@RequestBody @Valid EmailAuthRequestDto dto) {
        emailService.sendCode(dto.getPurpose(), dto.getEmail());
        return ApiResponse.success(EmailAuthResponseDto.builder()
                .createdAt(LocalDateTime.now())
                .build());
    }

    @PostMapping("/valid")
    @Operation(summary = "이메일 인증 확인")
    public ApiResponse<?> verifyCode(@RequestBody @Valid EmailValidRequestDto dto) {
        emailService.verify(dto.getPurpose(), dto.getEmail(), dto.getAuthCode());
        return ApiResponse.success(EmailValidResponseDto.builder()
                .createdAt(LocalDateTime.now())
                .build());
    }
}
