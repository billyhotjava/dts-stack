package com.yuzhi.dts.analytics.web.rest;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserResource {

    private final AnalyticsSessionService sessionService;

    public UserResource(AnalyticsSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping(path = "/current", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> current(HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(toMetabaseUser(user.get()));
    }

    private static Map<String, Object> toMetabaseUser(AnalyticsUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("email", user.getEmail());
        result.put("first_name", user.getFirstName());
        result.put("last_name", user.getLastName());
        result.put("common_name", "%s %s".formatted(user.getFirstName(), user.getLastName()).trim());
        result.put("is_active", user.isActive());
        result.put("is_superuser", user.isSuperuser());
        result.put("is_installer", true);
        result.put("is_qbnewb", false);
        result.put("group_ids", user.isSuperuser() ? List.of(1, 2) : List.of(2));
        result.put("personal_collection_id", user.getId());
        result.put("google_auth", false);
        result.put("ldap_auth", false);
        result.put("sso_source", null);
        result.put("login_attributes", Map.of());
        result.put("has_invited_second_user", false);
        result.put("has_question_and_dashboard", false);
        result.put("locale", null);
        result.put("date_joined", user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
        result.put("updated_at", user.getUpdatedAt() == null ? null : user.getUpdatedAt().toString());
        result.put("last_login", user.getUpdatedAt() == null ? null : user.getUpdatedAt().toString());
        result.put("first_login", user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
        return result;
    }
}

