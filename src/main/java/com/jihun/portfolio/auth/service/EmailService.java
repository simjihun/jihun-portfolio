package com.jihun.portfolio.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** 비밀번호 재설정 인증번호 이메일 발송. MAIL_USERNAME 미설정 시 발송하지 않고 로그로만 남긴다(로컬 개발용). */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean sendVerificationCode(String toEmail, String code) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("[mail] MAIL_USERNAME 미설정 — 이메일 발송 비활성화(로컬 확인용 코드: {})", code);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("[hunit.kr] 비밀번호 재설정 인증번호");
            message.setText("인증번호: " + code + "\n5분 이내에 입력해주세요.\n본인이 요청하지 않았다면 이 메일을 무시하세요.");
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.warn("[mail] 인증번호 발송 실패: {}", e.getMessage());
            return false;
        }
    }
}
