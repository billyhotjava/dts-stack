package com.yuzhi.dts.copilot.analytics.security;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.copilot.analytics.service.CopilotAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Validates API keys by delegating to copilot-ai's /internal/auth/verify endpoint.
 * Auto-provisions an AnalyticsUser on first login.
 */
@Service
public class ApiKeyAuthService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthService.class);

    private final CopilotAiClient copilotAiClient;
    private final AnalyticsUserRepository userRepository;

    public ApiKeyAuthService(CopilotAiClient copilotAiClient,
                             AnalyticsUserRepository userRepository) {
        this.copilotAiClient = copilotAiClient;
        this.userRepository = userRepository;
    }

    /**
     * Validate a raw API key by calling copilot-ai.
     * If valid, auto-provision an AnalyticsUser if one does not already exist.
     *
     * @param rawKey the raw API key
     * @return the authenticated user context, or empty if invalid
     */
    @Transactional
    public Optional<AuthenticatedUser> authenticate(String rawKey) {
        Optional<Map<String, Object>> verifyResult = copilotAiClient.verifyApiKey(rawKey);

        if (verifyResult.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> result = verifyResult.get();
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = (Map<String, Object>) result.get("user");

        if (userInfo == null) {
            return Optional.empty();
        }

        String userId = String.valueOf(userInfo.get("userId"));
        String userName = String.valueOf(userInfo.getOrDefault("userName", userId));

        // Auto-provision AnalyticsUser on first login
        AnalyticsUser analyticsUser = userRepository.findByUsernameIgnoreCase(userId)
                .orElseGet(() -> {
                    log.info("Auto-provisioning analytics user for: {}", userId);
                    AnalyticsUser newUser = new AnalyticsUser();
                    newUser.setUsername(userId);
                    newUser.setFirstName(userName);
                    newUser.setLastName("");
                    newUser.setPasswordHash("api-key-auth");
                    newUser.setSuperuser(false);
                    newUser.setActive(true);
                    // createdAt/updatedAt handled by @PrePersist
                    return userRepository.save(newUser);
                });

        // Touch to update last-seen timestamp via @PreUpdate
        userRepository.save(analyticsUser);

        return Optional.of(new AuthenticatedUser(analyticsUser, rawKey));
    }

    /**
     * Holds the authenticated AnalyticsUser and the raw API key for downstream use.
     */
    public record AuthenticatedUser(AnalyticsUser user, String apiKey) {}
}
