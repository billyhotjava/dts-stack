package com.yuzhi.dts.admin.service.pki;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class PkiChallengeService {

    public static final class Challenge {
        public final String id;
        public final String nonce;
        public final String aud;
        public final Instant ts;
        public final Instant exp;
        public final String ip;
        public final String ua;

        Challenge(String id, String nonce, String aud, Instant ts, Instant exp, String ip, String ua) {
            this.id = id; this.nonce = nonce; this.aud = aud; this.ts = ts; this.exp = exp; this.ip = ip; this.ua = ua;
        }
    }

    private final Map<String, Challenge> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public Challenge issue(String aud, String ip, String ua, Duration ttl) {
        String id = UUID.randomUUID().toString();
        byte[] buf = new byte[24];
        random.nextBytes(buf);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        Instant now = Instant.now();
        Instant exp = now.plus(ttl == null ? Duration.ofMinutes(10) : ttl);
        Challenge c = new Challenge(id, nonce, aud == null ? "dts-admin" : aud, now, exp, ip, ua);
        store.put(id, c);
        return c;
    }

    public Challenge peek(String id) {
        return store.get(id);
    }

    public Challenge validateAndConsume(String id, String providedNonce, String originDataBase64) {
        Challenge c = store.remove(id);
        if (c == null) return null;
        if (Instant.now().isAfter(c.exp)) return null;
        String expectedNonce = Objects.toString(c.nonce, "");
        if (providedNonce == null || !providedNonce.equals(expectedNonce)) {
            return null;
        }
        if (originDataBase64 != null) {
            try {
                byte[] decoded = Base64.getDecoder().decode(originDataBase64.replaceAll("\\s+", ""));
                String plain = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                if (!plain.contains(expectedNonce)) {
                    return null;
                }
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return c;
    }
}
