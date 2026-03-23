package com.yuzhi.dts.analytics.web.rest;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.ProjectCockpitService;
import com.yuzhi.dts.analytics.service.ProjectCockpitSettingsService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-cockpit")
@Transactional
public class ProjectCockpitResource {

    private final AnalyticsSessionService sessionService;
    private final ProjectCockpitService projectCockpitService;
    private final ProjectCockpitSettingsService projectCockpitSettingsService;

    public ProjectCockpitResource(
            AnalyticsSessionService sessionService,
            ProjectCockpitService projectCockpitService,
            ProjectCockpitSettingsService projectCockpitSettingsService) {
        this.sessionService = sessionService;
        this.projectCockpitService = projectCockpitService;
        this.projectCockpitSettingsService = projectCockpitSettingsService;
    }

    @GetMapping(path = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> summary(
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.summary(filters(majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/trends", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> trends(
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.trends(filters(majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/execution", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> execution(
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.execution(filters(majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/risk-attribution", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> riskAttribution(
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.riskAttribution(filters(majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/major-project-tree", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> majorProjectTree(
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.majorProjectTree(filters(majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/data-support", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dataSupport(
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.dataSupport(filters(majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/screen/metrics-overview", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> screenMetricsOverview(
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.screenMetricsOverview(filters(majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/screen/header", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> screenHeader(
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.screenHeader(filters(majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/screen/overview", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> screenOverview(
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.screenOverview(filters(majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/screen/execution", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> screenExecution(
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.screenExecution(filters(majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/screen/risk", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> screenRisk(
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.screenRisk(filters(majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/drill/{target}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> drillDetail(
            @PathVariable("target") String target,
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            @RequestParam(value = "dept", required = false) String extraDept,
            @RequestParam(value = "reason", required = false) String extraReason,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.drillDetail(
                target,
                filters(majorProjectId, dateFrom, dateTo, deptId, riskLevel),
                extraDept,
                extraReason));
    }

    @GetMapping(path = "/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> settings(HttpServletRequest request) {
        Optional<AnalyticsUser> user = currentUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }
        return ResponseEntity.ok(buildSettingsPayload(user.get()));
    }

    @PutMapping(path = "/settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateSettings(@RequestBody ProjectCockpitPeriodPayload payload, HttpServletRequest request) {
        Optional<AnalyticsUser> user = currentUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        try {
            projectCockpitSettingsService.savePublishedPeriod(
                    parseDate(payload.periodStart()),
                    parseDate(payload.periodEnd()),
                    user.get().getEmail());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body(exception.getMessage());
        }
    }

    private ResponseEntity<?> authorize(HttpServletRequest request, Object payload) {
        Optional<AnalyticsUser> user = currentUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }
        return ResponseEntity.ok(payload);
    }

    private ProjectCockpitService.Filters filters(
            String majorProjectId,
            String dateFrom,
            String dateTo,
            String deptId,
            String riskLevel) {
        Optional<ProjectCockpitSettingsService.PublishedPeriod> publishedPeriod = projectCockpitSettingsService.getPublishedPeriod();
        return new ProjectCockpitService.Filters(
                blankToNull(majorProjectId),
                parseDate(dateFrom, publishedPeriod.map(ProjectCockpitSettingsService.PublishedPeriod::periodStart).orElse(null)),
                parseDate(dateTo, publishedPeriod.map(ProjectCockpitSettingsService.PublishedPeriod::periodEnd).orElse(null)),
                blankToNull(deptId),
                blankToNull(riskLevel));
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        LocalDate parsed = parseDate(value);
        return parsed == null ? fallback : parsed;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Optional<AnalyticsUser> currentUser(HttpServletRequest request) {
        return MetabaseAuth.currentUser(sessionService, request);
    }

    private Map<String, Object> buildSettingsPayload(AnalyticsUser user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Optional<ProjectCockpitSettingsService.PublishedPeriod> publishedPeriod = projectCockpitSettingsService.getPublishedPeriod();
        payload.put("periodStart", publishedPeriod.map(ProjectCockpitSettingsService.PublishedPeriod::periodStart).map(LocalDate::toString).orElse(""));
        payload.put("periodEnd", publishedPeriod.map(ProjectCockpitSettingsService.PublishedPeriod::periodEnd).map(LocalDate::toString).orElse(""));
        payload.put("updatedBy", publishedPeriod.map(ProjectCockpitSettingsService.PublishedPeriod::updatedBy).orElse(""));
        payload.put("updatedAt", publishedPeriod.map(ProjectCockpitSettingsService.PublishedPeriod::updatedAt).map(Object::toString).orElse(""));
        payload.put("canPublish", user.isSuperuser());
        return payload;
    }

    public record ProjectCockpitPeriodPayload(String periodStart, String periodEnd) {}
}
