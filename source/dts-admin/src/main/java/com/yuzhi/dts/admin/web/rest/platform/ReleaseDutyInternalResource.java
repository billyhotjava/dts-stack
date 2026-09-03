package com.yuzhi.dts.admin.web.rest.platform;

import com.yuzhi.dts.admin.security.AdminInboundServiceAuthenticator;
import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.service.dto.keycloak.KeycloakUserDTO;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAdminClient;
import com.yuzhi.dts.admin.service.keycloak.KeycloakAuthService;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal current-duty lookup for the release reconciler.
 *
 * <p>The caller is authenticated with a pairwise service token. This endpoint never accepts or
 * forwards an end-user token and only exposes the three release duties.
 */
@RestController
@RequestMapping("/api/platform/internal/release-duties")
public class ReleaseDutyInternalResource {

    private static final String PLATFORM_SERVICE = "dts-platform";
    // Mirrors the platform's temporary menu-level projection; it does not create new realm roles.
    private static final Map<String, Set<String>> DUTY_ROLES = Map.of(
        "MODEL_MAINTAINER",
        Set.of(AuthoritiesConstants.MODEL_MAINTAINERS),
        "RELEASE_REVIEWER",
        Set.of(AuthoritiesConstants.MODEL_RELEASE_REVIEWERS),
        "RELEASE_OPERATOR",
        Set.of(AuthoritiesConstants.MODEL_RELEASE_OPERATORS)
    );

    private final AdminInboundServiceAuthenticator authenticator;
    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakAdminClient keycloakAdminClient;

    @Value("${dts.keycloak.admin-client-id:${OAUTH2_ADMIN_CLIENT_ID:}}")
    private String managementClientId;

    @Value(
        "${dts.keycloak.admin-client-secret:${OAUTH2_ADMIN_CLIENT_SECRET:}}"
    )
    private String managementClientSecret;

    public ReleaseDutyInternalResource(
        AdminInboundServiceAuthenticator authenticator,
        KeycloakAuthService keycloakAuthService,
        KeycloakAdminClient keycloakAdminClient
    ) {
        this.authenticator = authenticator;
        this.keycloakAuthService = keycloakAuthService;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<DutyCheckResponse>> check(
        @RequestParam String actorId,
        @RequestParam String duty,
        HttpServletRequest request
    ) {
        if (!authenticator.authenticate(request, PLATFORM_SERVICE).accepted()) {
            return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("service authentication failed"));
        }
        if (
            !StringUtils.hasText(actorId) ||
            actorId.trim().length() > 128 ||
            !StringUtils.hasText(duty) ||
            duty.trim().length() > 64
        ) {
            return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("actorId and duty are invalid"));
        }
        String actor = actorId.trim();
        String requestedDuty = duty.trim().toUpperCase(Locale.ROOT);
        Set<String> requiredRoles = DUTY_ROLES.get(requestedDuty);
        if (requiredRoles == null) {
            return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("unsupported release duty"));
        }
        try {
            String token = adminAccessToken();
            Optional<KeycloakUserDTO> user =
                keycloakAdminClient
                    .findById(actor, token)
                    .or(() ->
                        keycloakAdminClient.findByUsernameStrict(actor, token)
                    );
            if (user.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            KeycloakUserDTO current = user.orElseThrow();
            boolean active = !Boolean.FALSE.equals(current.getEnabled());
            List<String> roles = active
                ? Optional
                    .ofNullable(
                        keycloakAdminClient.listUserRealmRoles(
                            current.getId(),
                            token
                        )
                    )
                    .orElse(List.of())
                : List.of();
            boolean hasDuty = roles.stream().anyMatch(requiredRoles::contains);
            return ResponseEntity.ok(
                ApiResponse.ok(
                    new DutyCheckResponse(
                        actor,
                        requestedDuty,
                        hasDuty,
                        Instant.now()
                    )
                )
            );
        } catch (RuntimeException failure) {
            return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("release duty directory unavailable"));
        }
    }

    private String adminAccessToken() {
        var response = keycloakAuthService.obtainClientCredentialsToken(
            managementClientId,
            managementClientSecret
        );
        if (
            response == null ||
            !StringUtils.hasText(response.accessToken())
        ) {
            throw new IllegalStateException(
                "Keycloak management access token is unavailable"
            );
        }
        return response.accessToken();
    }

    public record DutyCheckResponse(
        String actorId,
        String duty,
        boolean hasDuty,
        Instant checkedAt
    ) {}
}
