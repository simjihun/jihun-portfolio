package com.jihun.portfolio.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 전화번호·이메일처럼 "저장은 하되 평문으로 남기면 안 되는" 개인정보를 위한 암호화 유틸.
 *
 * - encrypt/decrypt: AES-256-GCM. app.security.encryption-key(환경변수 APP_ENCRYPTION_KEY)를
 *   SHA-256으로 늘려 32바이트 키로 쓴다. DB가 그대로 유출돼도 이 키 없이는 원문을 복원할 수 없다.
 * - lookupHash: HMAC-SHA256 기반의 "조회 전용" 해시. 같은 입력이면 항상 같은 해시가 나오므로
 *   "이미 가입된 이메일인지" 같은 중복확인·조회에 쓸 수 있지만, 해시에서 원문을 되돌릴 방법은 없다.
 */
@Service
public class CryptoService {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${app.security.encryption-key:}")
    private String rawKey;

    private byte[] derivedKeyBytes() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return sha.digest((rawKey == null ? "" : rawKey).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("암호화 키 초기화 실패", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(derivedKeyBytes(), "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(derivedKeyBytes(), "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("복호화 실패", e);
        }
    }

    /** 대소문자·공백 차이로 같은 값이 다르게 해시되지 않도록 정규화 후 HMAC-SHA256. */
    public String lookupHash(String plain) {
        if (plain == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(derivedKeyBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(normalize(plain).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("조회용 해시 생성 실패", e);
        }
    }

    private String normalize(String s) {
        return s.strip().toLowerCase();
    }
}
