package com.yuzhi.dts.copilot.analytics.web.support;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class MetabaseAuth {

    private MetabaseAuth() {}

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
        if (!user.get().isSuperuser()) {
            return Optional.of(
                    ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that."));
        }
        return Optional.empty();
    }

    public static Optional<AnalyticsUser> currentUser(AnalyticsSessionService sessionService, HttpServletRequest request) {
        return sessionService.resolveUser(request);
    }

    public static Optional<Long> getUserId(AnalyticsSessionService sessionService, HttpServletRequest request) {
        return sessionService.resolveUser(request).map(AnalyticsUser::getId);
    }
}

