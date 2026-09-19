package com.ontheway.service;

import com.ontheway.dto.request.MemberLoginRequestDto;
import com.ontheway.dto.response.MemberLoginResponseDto;
import com.ontheway.entity.User;
import com.ontheway.global.exception.BusinessException;
import com.ontheway.global.exception.ErrorCode;
import com.ontheway.global.security.jwt.JwtTokenProvider;
import com.ontheway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public MemberLoginResponseDto login(MemberLoginRequestDto dto) {
        User user = userRepository.findByAccountIdAndDeletedAtIsNull(dto.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        String token = jwtTokenProvider.createToken(user.getAccountId());

        return MemberLoginResponseDto.builder()
                .accessToken(token)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
