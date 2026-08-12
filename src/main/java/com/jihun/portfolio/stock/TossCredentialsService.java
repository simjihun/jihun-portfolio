package com.jihun.portfolio.stock;

import com.jihun.portfolio.auth.domain.Member;
import com.jihun.portfolio.auth.repository.MemberRepository;
import com.jihun.portfolio.auth.service.CryptoService;
import org.springframework.stereotype.Service;

/**
 * 토스증권 Open API 키(client_id/secret) 보관소.
 * conf/app.conf(평문 파일) 대신 관리자(simering) 계정 레코드에 CryptoService(AES-GCM)로 암호화해
 * 저장한다 — AI 주식(TossApiClient 시세 조회)과 TOSS 주식(계좌 조회) 두 기능 모두 이 서비스를 통해
 * 같은 키를 공유해서 쓴다.
 *
 * 관리자명은 MemberService.BOOTSTRAP_ADMIN_USERNAME("simering")과 동일해야 한다 — 이 계정은
 * 서버 시작 시 항상 존재가 보장되므로 키를 안전하게 귀속시킬 수 있다.
 */
@Service
public class TossCredentialsService {

    private static final String KEY_OWNER_USERNAME = "simering";

    private final MemberRepository memberRepository;
    private final CryptoService crypto;

    private volatile String cachedClientId;
    private volatile String cachedClientSecret;
    private volatile boolean loaded = false;

    public TossCredentialsService(MemberRepository memberRepository, CryptoService crypto) {
        this.memberRepository = memberRepository;
        this.crypto = crypto;
    }

    public String getClientId() {
        ensureLoaded();
        return cachedClientId;
    }

    public String getClientSecret() {
        ensureLoaded();
        return cachedClientSecret;
    }

    public boolean isConfigured() {
        ensureLoaded();
        return cachedClientId != null && !cachedClientId.isBlank()
                && cachedClientSecret != null && !cachedClientSecret.isBlank();
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        reload();
    }

    /** DB에서 다시 읽어 캐시를 갱신한다(관리자가 설정 화면에서 키를 새로 저장했을 때 자동 호출됨). */
    public synchronized void reload() {
        var memberOpt = memberRepository.findByUsername(KEY_OWNER_USERNAME);
        if (memberOpt.isEmpty()) {
            cachedClientId = null;
            cachedClientSecret = null;
        } else {
            Member m = memberOpt.get();
            cachedClientId = m.getTossClientIdEncrypted() != null ? crypto.decrypt(m.getTossClientIdEncrypted()) : null;
            cachedClientSecret = m.getTossClientSecretEncrypted() != null ? crypto.decrypt(m.getTossClientSecretEncrypted()) : null;
        }
        loaded = true;
    }

    /** 관리자 설정 화면에서 키를 저장할 때 사용. 빈 값을 넘기면 해당 키를 지운다. */
    public synchronized void save(String clientId, String clientSecret) {
        Member m = memberRepository.findByUsername(KEY_OWNER_USERNAME)
                .orElseThrow(() -> new IllegalStateException("관리자(simering) 계정을 찾을 수 없습니다"));
        m.setTossClientIdEncrypted((clientId != null && !clientId.isBlank()) ? crypto.encrypt(clientId.trim()) : null);
        m.setTossClientSecretEncrypted((clientSecret != null && !clientSecret.isBlank()) ? crypto.encrypt(clientSecret.trim()) : null);
        memberRepository.save(m);
        reload();
    }
}
