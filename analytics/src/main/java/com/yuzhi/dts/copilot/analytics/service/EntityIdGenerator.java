package com.yuzhi.dts.copilot.analytics.service;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class EntityIdGenerator {

    private final SecureRandom random = new SecureRandom();

    public String newEntityId() {
        byte[] bytes = new byte[15]; // 15 bytes -> 20 chars base64url without padding
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

