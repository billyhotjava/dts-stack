package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsPasswordResetToken;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsLoginHistory;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsPasswordResetTokenRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsLoginHistoryRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseCookies;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
@ConditionalOnProperty(prefix = "dts.analytics.platform-auth", name = "enabled", havingValue = "false", matchIfMissing = true)
public class SessionResource {

    private final AnalyticsUserRepository userRepository;
    private final AnalyticsSessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final AnalyticsPasswordResetTokenRepository passwordResetTokenRepository;
    private final AnalyticsLoginHistoryRepository loginHistoryRepository;

    public SessionResource(
            AnalyticsUserRepository userRepository,
            AnalyticsSessionService sessionService,
            PasswordEncoder passwordEncoder,
            AnalyticsPasswordResetTokenRepository passwordResetTokenRepository,
            AnalyticsLoginHistoryRepository loginHistoryRepository) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.loginHistoryRepository = loginHistoryRepository;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody SessionRequest request, HttpServletRequest servletRequest) {
        boolean secure = "https".equalsIgnoreCase(servletRequest.getHeader("X-Forwarded-Proto"))
                || "https".equalsIgnoreCase(servletRequest.getScheme());

        UUID deviceId = UUID.randomUUID();
        String username = request == null ? "" : Objects.toString(request.username(), "").trim();
        String password = request == null ? "" : Objects.toString(request.password(), "");

        Optional<AnalyticsUser> user = userRepository.findByUsernameIgnoreCase(username);
        if (user.isEmpty() || !user.get().isActive() || !passwordEncoder.matches(password, user.get().getPasswordHash())) {
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(401).contentType(MediaType.APPLICATION_JSON);
            for (String cookie : MetabaseCookies.deviceCookieHeaders(deviceId, secure)) {
                builder.header("Set-Cookie", cookie);
            }
            return builder.body(Map.of("errors", Map.of("password", "did not match stored password")));
        }

        UUID sessionId = sessionService.createSession(user.get().getId());
        AnalyticsLoginHistory history = new AnalyticsLoginHistory();
        history.setUserId(user.get().getId());
        history.setIpAddress(resolveClientIp(servletRequest));
        history.setUserAgent(servletRequest.getHeader("User-Agent"));
        loginHistoryRepository.save(history);

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON);
        for (String cookie : MetabaseCookies.loginCookieHeaders(sessionId, deviceId, secure)) {
            builder.header("Set-Cookie", cookie);
        }
        return builder.body(Map.of("id", sessionId.toString()));
    }

    @DeleteMapping
    public ResponseEntity<?> delete(HttpServletRequest request) {
        boolean secure = "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")) || "https".equalsIgnoreCase(request.getScheme());
        sessionService.resolveSessionId(request).ifPresent(sessionService::revokeSession);

        ResponseEntity.HeadersBuilder<?> builder = ResponseEntity.noContent();
        for (String cookie : MetabaseCookies.logoutCookieHeaders(secure)) {
            builder.header("Set-Cookie", cookie);
        }
        return builder.build();
    }

    @GetMapping(path = "/google_auth", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> googleAuth() {
        return ResponseEntity.ok(Map.of("enabled", false, "configured", false));
    }

    @PostMapping(path = "/forgot_password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        String username = request == null ? "" : Objects.toString(request.username(), "").trim();
        userRepository.findByUsernameIgnoreCase(username).ifPresent(user -> {
            AnalyticsPasswordResetToken token = new AnalyticsPasswordResetToken();
            token.setToken(UUID.randomUUID().toString());
            token.setUserId(user.getId());
            token.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
            passwordResetTokenRepository.save(token);
        });
        return ResponseEntity.ok(Map.of());
    }

    @GetMapping(path = "/password_reset_token_valid", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> passwordResetTokenValidGet(@RequestParam(name = "token", required = false) String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("token", "Token is required.")));
        }
        boolean valid = passwordResetTokenRepository.findByTokenAndUsedFalseAndExpiresAtAfter(token.trim(), Instant.now()).isPresent();
        if (!valid) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("token", "Token is invalid.")));
        }
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping(path = "/password_reset_token_valid", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> passwordResetTokenValidPost(@RequestBody PasswordResetTokenValidRequest request) {
        String token = request == null ? "" : Objects.toString(request.token(), "").trim();
        return passwordResetTokenValidGet(token);
    }

    @PostMapping(path = "/reset_password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        String token = request == null ? "" : Objects.toString(request.token(), "").trim();
        String password = request == null ? "" : Objects.toString(request.password(), "");
        if (!isPasswordAcceptable(password)) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("password", "password is too common.")));
        }

        Optional<AnalyticsPasswordResetToken> resetToken = passwordResetTokenRepository.findByTokenAndUsedFalseAndExpiresAtAfter(token, Instant.now());
        if (resetToken.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("token", "Token is invalid.")));
        }

        AnalyticsPasswordResetToken record = resetToken.get();
        AnalyticsUser user = userRepository.findById(record.getUserId()).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("token", "Token is invalid.")));
        }

        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
        record.setUsed(true);
        passwordResetTokenRepository.save(record);
        return ResponseEntity.ok(Map.of());
    }

    public record SessionRequest(@JsonProperty("username") String username, @JsonProperty("password") String password) {}

    public record ForgotPasswordRequest(@JsonProperty("username") String username) {}

    public record PasswordResetTokenValidRequest(@JsonProperty("token") String token) {}

    public record ResetPasswordRequest(@JsonProperty("token") String token, @JsonProperty("password") String password) {}

    private static boolean isPasswordAcceptable(String password) {
        if (password == null || password.length() < 6) {
            return false;
        }
        for (int i = 0; i < password.length(); i++) {
            if (Character.isDigit(password.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
