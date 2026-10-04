package com.yuzhi.dts.ingestion.security;

import java.security.Principal;
import org.springframework.util.StringUtils;

/**
 * Marks a user identity asserted by a trusted internal service after its
 * pairwise service credential has been verified.
 */
public record ForwardedUserPrincipal(String username, String sourceService) implements Principal {

    public ForwardedUserPrincipal {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(sourceService)) {
            throw new IllegalArgumentException("forwarded user identity and source service are required");
        }
        username = username.trim();
        sourceService = sourceService.trim();
    }

    @Override
    public String getName() {
        return username;
    }
}
