package com.ontheway.infra.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component("verificationMailSender")
@RequiredArgsConstructor
@Slf4j
public class MailSender {
    private final JavaMailSender javaMailSender;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Async
    public void send(String to, String subject, String text) {
        if (!mailEnabled) {
            log.info("[DEV] mail skipped -> to: {}, subject: {}, text: {}", to, subject, text);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        javaMailSender.send(message);
    }

}
