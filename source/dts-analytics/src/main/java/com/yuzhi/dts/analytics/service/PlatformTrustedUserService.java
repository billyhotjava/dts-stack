package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.config.PlatformAuthProperties;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import jakarta.servlet.http.HttpServletRequest;
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

    public PlatformTrustedUserService(
            PlatformAuthProperties properties,
            AnalyticsUserRepository userRepository,
            GroupService groupService,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.groupService = groupService;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<AnalyticsUser> resolveOrProvision(HttpServletRequest request) {
        if (!properties.enabled()) {
            return Optional.empty();
        }

        if (properties.requireForwardedHeaders()) {
            // Basic hardening: only trust these headers when request comes via reverse-proxy.
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
        String email = deriveEmail(platformUserId, username);

        boolean superuser = isSuperuser(header(request, "X-DTS-Roles"));

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
}

