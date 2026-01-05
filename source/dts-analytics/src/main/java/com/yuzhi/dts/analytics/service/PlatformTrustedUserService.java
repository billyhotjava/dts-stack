package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.config.PlatformAuthProperties;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class PlatformTrustedUserService {

    private final PlatformAuthProperties properties;
    private final AnalyticsUserRepository userRepository;
    private final GroupService groupService;
    private final PasswordEncoder passwordEncoder;
    private final HttpClient httpClient;

    public PlatformTrustedUserService(
            PlatformAuthProperties properties,
            AnalyticsUserRepository userRepository,
            GroupService groupService,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.groupService = groupService;
        this.passwordEncoder = passwordEncoder;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public Optional<AnalyticsUser> resolveOrProvision(HttpServletRequest request) {
        if (!properties.enabled()) {
            return Optional.empty();
        }

        Optional<PlatformIdentity> identity = resolveIdentity(request);
        if (identity.isEmpty()) {
            return Optional.empty();
        }

        String username = identity.get().username();
        String displayName = identity.get().displayName();
        String platformUserId = identity.get().platformUserId();
        String email = deriveEmail(platformUserId, username);
        boolean superuser = identity.get().superuser();

        AnalyticsUser user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user != null) {
            boolean dirty = false;
            if (!displayName.equals(user.getFirstName())) {
                user.setFirstName(displayName);
                dirty = true;
            }
            if (user.isSuperuser() != superuser) {
                user.setSuperuser(superuser);
                dirty = true;
            }
            if (!user.isActive()) {
                user.setActive(true);
                dirty = true;
            }
            if (dirty) {
                user = userRepository.save(user);
            }
            groupService.ensureUserInDefaultGroups(user);
            return Optional.of(user);
        }

        AnalyticsUser created = new AnalyticsUser();
        created.setEmail(email);
        created.setFirstName(displayName);
        created.setLastName("");
        created.setSuperuser(superuser);
        created.setActive(true);
        created.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));

        try {
            created = userRepository.save(created);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent provisioning; refetch by stable key.
            AnalyticsUser existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
            if (existing != null) {
                groupService.ensureUserInDefaultGroups(existing);
                return Optional.of(existing);
            }
            throw ex;
        }

        groupService.ensureUserInDefaultGroups(created);
        return Optional.of(created);
    }

    private Optional<PlatformIdentity> resolveIdentity(HttpServletRequest request) {
        Optional<PlatformIdentity> fromHeaders = resolveIdentityFromForwardedHeaders(request);
        if (fromHeaders.isPresent()) {
            return fromHeaders;
        }
        if (!Boolean.TRUE.equals(properties.allowBearerFallback())) {
            return Optional.empty();
        }
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization)) {
            return Optional.empty();
        }
        if (!authorization.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return Optional.empty();
        }
        return resolveIdentityViaPlatformForwardAuth(authorization, request.getHeader("Cookie"));
    }

    private Optional<PlatformIdentity> resolveIdentityFromForwardedHeaders(HttpServletRequest request) {
        if (properties.requireForwardedHeaders()) {
            // Basic hardening: only trust identity headers when request comes via reverse-proxy.
            String forwardedHost = header(request, "X-Forwarded-Host");
            String forwardedProto = header(request, "X-Forwarded-Proto");
            if (!StringUtils.hasText(forwardedHost) && !StringUtils.hasText(forwardedProto)) {
                return Optional.empty();
            }
        }

        String username = header(request, "X-DTS-User");
        if (!StringUtils.hasText(username)) {
            return Optional.empty();
        }

        String displayName = header(request, "X-DTS-Display-Name");
        if (!StringUtils.hasText(displayName)) {
            displayName = username;
        }
        String platformUserId = header(request, "X-DTS-User-Id");
        boolean superuser = isSuperuser(header(request, "X-DTS-Roles"));
        return Optional.of(new PlatformIdentity(username, displayName, platformUserId, superuser));
    }

    private Optional<PlatformIdentity> resolveIdentityViaPlatformForwardAuth(String authorization, String cookie) {
        URI uri;
        try {
            uri = URI.create(properties.forwardAuthUrl());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .GET()
                .header("Authorization", authorization.trim());
        if (cookie != null && !cookie.isBlank()) {
            builder.header("Cookie", cookie);
        }
        if (properties.forwardAuthTimeoutMs() > 0) {
            builder.timeout(java.time.Duration.ofMillis(properties.forwardAuthTimeoutMs()));
        }

        HttpResponse<Void> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException ex) {
            return Optional.empty();
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return Optional.empty();
        }

        String username = response.headers().firstValue("X-DTS-User").orElse("");
        if (!StringUtils.hasText(username)) {
            return Optional.empty();
        }

        String displayName = response.headers().firstValue("X-DTS-Display-Name").orElse("");
        if (!StringUtils.hasText(displayName)) {
            displayName = username;
        }
        String platformUserId = response.headers().firstValue("X-DTS-User-Id").orElse("");
        String roles = response.headers().firstValue("X-DTS-Roles").orElse("");
        boolean superuser = isSuperuser(roles);
        return Optional.of(new PlatformIdentity(username, displayName, platformUserId, superuser));
    }

    private boolean isSuperuser(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return false;
        }
        List<String> roles = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (roles.isEmpty()) {
            return false;
        }
        for (String role : roles) {
            String normalized = role.trim();
            if (!normalized.isEmpty() && properties.superuserRoles().contains(normalized)) {
                return true;
            }
            if (normalized.toUpperCase(Locale.ROOT).contains("OP_ADMIN")) {
                return true;
            }
        }
        return false;
    }

    private String deriveEmail(String platformUserId, String username) {
        String base = StringUtils.hasText(platformUserId) ? platformUserId.trim() : username.trim();
        if (base.contains("@")) {
            return base.toLowerCase(Locale.ROOT);
        }
        return (base + "@" + properties.emailDomain()).toLowerCase(Locale.ROOT);
    }

    private static String header(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        return v == null ? "" : v.trim();
    }

    private record PlatformIdentity(String username, String displayName, String platformUserId, boolean superuser) {}
}
