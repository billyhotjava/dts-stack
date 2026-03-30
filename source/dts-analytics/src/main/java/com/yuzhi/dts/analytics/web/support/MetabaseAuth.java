package com.yuzhi.dts.analytics.web.support;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class MetabaseAuth {

    private MetabaseAuth() {}

    public static final Set<String> DATA_ADMIN_ROLES = new HashSet<>(
            Arrays.asList("ROLE_OP_ADMIN", "ROLE_INST_DATA_OWNER", "ROLE_INST_LEADER"));

    public static Optional<ResponseEntity<String>> requireUser(AnalyticsSessionService sessionService, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return Optional.of(ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated"));
        }
        return Optional.empty();
    }

    public static Optional<ResponseEntity<String>> requireSuperuser(AnalyticsSessionService sessionService, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return Optional.of(ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated"));
        }
        if (!user.orElseThrow().isSuperuser()) {
            return Optional.of(
                    ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that."));
        }
        return Optional.empty();
    }

    public static Optional<ResponseEntity<String>> requireDataAdmin(AnalyticsSessionService sessionService, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return Optional.of(ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated"));
        }
        if (user.orElseThrow().isSuperuser()) {
            return Optional.empty();
        }
        if (!isDataAdmin(request)) {
            return Optional.of(
                    ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that."));
        }
        return Optional.empty();
    }

    public static boolean isDataAdmin(HttpServletRequest request) {
        String rolesHeader = request.getHeader("X-DTS-Roles");
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return false;
        }
        for (String role : rolesHeader.split(",")) {
            if (DATA_ADMIN_ROLES.contains(role.trim())) {
                return true;
            }
        }
        return false;
    }

    public static Optional<AnalyticsUser> currentUser(AnalyticsSessionService sessionService, HttpServletRequest request) {
        return sessionService.resolveUser(request);
    }

    public static Optional<Long> getUserId(AnalyticsSessionService sessionService, HttpServletRequest request) {
        return sessionService.resolveUser(request).map(AnalyticsUser::getId);
    }
}

