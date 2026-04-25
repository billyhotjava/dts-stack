package com.yuzhi.dts.analytics.web.rest.internal;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.web.rest.internal.dto.ScreenSummaryDto;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint-17 / F1 — internal endpoint exposing the screen catalog to
 * trusted sister services (currently dts-platform's reconcile job).
 *
 * <p>Auth model: this endpoint accepts the {@code X-DTS-Service} header
 * issued by other DTS services. Same trust model as the existing
 * {@code PlatformPermissionClient} → dts-platform call chain. Allowed
 * service names are gated to a small allow-list so a stray client cannot
 * scrape the screen list. Any unauthorized request gets a 403.
 *
 * <p>Path is {@code /api/internal/screens} — distinct from the
 * user-facing {@code /api/screens} resource so existing UI flows are
 * untouched.
 */
@RestController
@RequestMapping("/api/internal/screens")
public class InternalScreenResource {

    private static final Logger log = LoggerFactory.getLogger(InternalScreenResource.class);
    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final Set<String> ALLOWED_SERVICES = Set.of("dts-platform");

    private final AnalyticsScreenRepository screenRepository;

    public InternalScreenResource(AnalyticsScreenRepository screenRepository) {
        this.screenRepository = screenRepository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        String service = request.getHeader(SERVICE_HEADER);
        if (!StringUtils.hasText(service) || !ALLOWED_SERVICES.contains(service.trim())) {
            log.debug("internal screens denied: service={}", service);
            return ResponseEntity.status(403).build();
        }

        List<ScreenSummaryDto> result = screenRepository
            .findAllByOrderByIdDesc()
            .stream()
            .map(InternalScreenResource::toDto)
            .toList();

        return ResponseEntity.ok(result);
    }

    private static ScreenSummaryDto toDto(AnalyticsScreen s) {
        return new ScreenSummaryDto(
            s.getId(),
            s.getName(),
            s.getDescription(),
            s.getClassification(),
            s.getOwnerDeptCode(),
            s.isArchived(),
            s.getUpdatedAt()
        );
    }
}
