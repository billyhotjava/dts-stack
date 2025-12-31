package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.web.support.MetabaseCookies;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class SessionResource {

    private final AnalyticsUserRepository userRepository;
    private final AnalyticsSessionService sessionService;
    private final PasswordEncoder passwordEncoder;

    public SessionResource(
            AnalyticsUserRepository userRepository, AnalyticsSessionService sessionService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody SessionRequest request, HttpServletRequest servletRequest) {
        boolean secure = "https".equalsIgnoreCase(servletRequest.getHeader("X-Forwarded-Proto"))
                || "https".equalsIgnoreCase(servletRequest.getScheme());

        UUID deviceId = UUID.randomUUID();
        String username = request == null ? "" : Objects.toString(request.username(), "").trim();
        String password = request == null ? "" : Objects.toString(request.password(), "");

        Optional<AnalyticsUser> user = userRepository.findByEmailIgnoreCase(username);
        if (user.isEmpty() || !passwordEncoder.matches(password, user.get().getPasswordHash())) {
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(401).contentType(MediaType.APPLICATION_JSON);
            for (String cookie : MetabaseCookies.deviceCookieHeaders(deviceId, secure)) {
                builder.header("Set-Cookie", cookie);
            }
            return builder.body(Map.of("errors", Map.of("password", "did not match stored password")));
        }

        UUID sessionId = sessionService.createSession(user.get().getId());
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

    public record SessionRequest(@JsonProperty("username") String username, @JsonProperty("password") String password) {}
}
