package com.ontheway.service;

import com.ontheway.enums.EmailPurpose;
import com.ontheway.global.exception.BusinessException;
import com.ontheway.global.exception.ErrorCode;
import com.ontheway.global.util.VerificationKeyUtil;
import com.ontheway.infra.cache.VerificationCodeStore;
import com.ontheway.infra.mail.MailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class EmailService {
    private static final int MAX_ATTEMPT = 5;

    private final MailSender mailSender;
    private final VerificationCodeStore codeStore;

    public void sendCode(EmailPurpose purpose, String email) {
        String key = VerificationKeyUtil.of(purpose, email);
        String code = generateCode();

        codeStore.save(key, code);
        codeStore.resetAttempt(key);   // 새 코드 발급 시도 횟수 초기화
        mailSender.send(email, "[OnTheWay] 인증번호 안내", "인증번호: "+code);
    }

    public void verify(EmailPurpose purpose, String email, String input) {
        String key = VerificationKeyUtil.of(purpose, email);
        String saved = codeStore.find(key)
                .orElseThrow(() -> new BusinessException(ErrorCode.CODE_EXPIRED));

        if (codeStore.increaseAttempt(key) > MAX_ATTEMPT) {
            codeStore.delete(key);
            codeStore.resetAttempt(key);
            throw new BusinessException(ErrorCode.TOO_MANY_ATTEMPTS);
        }

        if (!saved.equals(input)) {
            throw new BusinessException(ErrorCode.CODE_MISMATCH);
        }
        codeStore.delete(key);
        codeStore.resetAttempt(key);
        codeStore.markVerified(key);
    }

    private String generateCode() {
        return String.format("%04d", new SecureRandom().nextInt(10000));
    }
}
