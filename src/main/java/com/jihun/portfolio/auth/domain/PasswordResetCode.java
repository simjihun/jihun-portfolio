package com.jihun.portfolio.auth.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 비밀번호 재설정용 인증번호 1건.
 * verified=true가 되면 resetToken이 발급되고, 이 토큰으로만 실제 비밀번호 변경(reset)이 가능하다 —
 * 인증번호 자체를 다시 비밀번호 변경 API에 넘기지 않도록 분리해, 인증번호가 노출돼도 토큰 없이는
 * 비밀번호를 바꿀 수 없게 한다.
 */
@Entity
@Table(name = "password_reset_code")
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 10)
    private String code;

    /** EMAIL만 실제 발송 구현. SMS는 값은 저장 가능하지만 현재 발송 연동 전(추후 확장 지점). */
    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean verified = false;

    @Column(nullable = false)
    private boolean used = false;

    @Column(length = 100)
    private String resetToken;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected PasswordResetCode() {
        // JPA 기본 생성자
    }

    public PasswordResetCode(Long memberId, String code, String channel, LocalDateTime expiresAt) {
        this.memberId = memberId;
        this.code = code;
        this.channel = channel;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getCode() { return code; }
    public String getChannel() { return channel; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
