package com.jihun.portfolio.auth.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 회원.
 * 비밀번호는 BCrypt 해시로만 저장한다(복호화 불가 — 로그인 시 입력값을 같은 방식으로 해시해 비교).
 * 전화번호·이메일은 AES-GCM으로 암호화해 저장하고, 중복확인·조회용으로는 별도의 조회용 해시(HMAC)를
 * 함께 저장한다 — 조회 해시는 원문으로 되돌릴 수 없어서, DB가 유출돼도 평문 전화번호·이메일이 그대로
 * 노출되지 않으면서도 "이미 가입된 이메일인지" 같은 조회는 계속 할 수 있다.
 */
@Entity
@Table(name = "member", indexes = {
        @Index(name = "idx_member_username", columnList = "username", unique = true),
        @Index(name = "idx_member_email_hash", columnList = "emailLookupHash"),
        @Index(name = "idx_member_phone_hash", columnList = "phoneLookupHash")
})
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false, length = 500)
    private String phoneEncrypted;

    @Column(nullable = false, length = 100)
    private String phoneLookupHash;

    @Column(nullable = false, length = 500)
    private String emailEncrypted;

    @Column(nullable = false, length = 100)
    private String emailLookupHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role = MemberRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status = MemberStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Member() {
        // JPA 기본 생성자
    }

    public Member(String username, String passwordHash, String name,
                  String phoneEncrypted, String phoneLookupHash,
                  String emailEncrypted, String emailLookupHash,
                  MemberRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phoneEncrypted = phoneEncrypted;
        this.phoneLookupHash = phoneLookupHash;
        this.emailEncrypted = emailEncrypted;
        this.emailLookupHash = emailLookupHash;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getName() { return name; }
    public String getPhoneEncrypted() { return phoneEncrypted; }
    public String getPhoneLookupHash() { return phoneLookupHash; }
    public String getEmailEncrypted() { return emailEncrypted; }
    public String getEmailLookupHash() { return emailLookupHash; }
    public MemberRole getRole() { return role; }
    public MemberStatus getStatus() { return status; }
    public void setStatus(MemberStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
