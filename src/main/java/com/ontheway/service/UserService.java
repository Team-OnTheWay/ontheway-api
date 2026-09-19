package com.ontheway.service;

import com.ontheway.dto.request.MemberCheckIdReqeustDto;
import com.ontheway.dto.request.MemberSaveRequestDto;
import com.ontheway.dto.response.MemberDetailResponseDto;
import com.ontheway.entity.User;
import com.ontheway.enums.EmailPurpose;
import com.ontheway.global.exception.BusinessException;
import com.ontheway.global.exception.ErrorCode;
import com.ontheway.global.util.VerificationKeyUtil;
import com.ontheway.infra.cache.VerificationCodeStore;
import com.ontheway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final String SIGNUP_PREFIX = "SIGNUP:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeStore codeStore;

    public boolean isDuplicated(MemberCheckIdReqeustDto dto) {
        return userRepository.existsByAccountId(dto.getUserId());
    }

    public void signup(MemberSaveRequestDto dto) {
        String verifyKey = VerificationKeyUtil.of(EmailPurpose.SIGN_UP, dto.getEmail());

        if (userRepository.existsByAccountId(dto.getUserId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (!codeStore.isVerified(verifyKey)) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        User user = User.builder()
                .accountId(dto.getUserId())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .name(dto.getUserName())
                .nickname(dto.getNickName())
                .birthDate(LocalDate.parse(dto.getBirthday()))
                .termsAgreedAt(LocalDateTime.now())
                .build();
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        codeStore.removeVerified(verifyKey);
    }

    public MemberDetailResponseDto getInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return MemberDetailResponseDto.builder()
                .userId(user.getAccountId())
                .email(user.getEmail())
                .nickName(user.getNickname())
                .birthday(String.valueOf(user.getBirthDate()))
                .build();
    }

}
