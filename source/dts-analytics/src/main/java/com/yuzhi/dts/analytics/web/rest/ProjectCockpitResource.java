package com.yuzhi.dts.analytics.web.rest;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.ProjectCockpitService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-cockpit")
@Transactional
public class ProjectCockpitResource {

    private final AnalyticsSessionService sessionService;
    private final ProjectCockpitService projectCockpitService;

    public ProjectCockpitResource(AnalyticsSessionService sessionService, ProjectCockpitService projectCockpitService) {
        this.sessionService = sessionService;
        this.projectCockpitService = projectCockpitService;
    }

    @GetMapping(path = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> summary(
            @RequestParam(value = "programId", required = false) String programId,
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.summary(filters(programId, majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/trends", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> trends(
            @RequestParam(value = "programId", required = false) String programId,
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.trends(filters(programId, majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/execution", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> execution(
            @RequestParam(value = "programId", required = false) String programId,
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.execution(filters(programId, majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/risk-attribution", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> riskAttribution(
            @RequestParam(value = "programId", required = false) String programId,
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.riskAttribution(filters(programId, majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/major-project-tree", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> majorProjectTree(
            @RequestParam(value = "programId", required = false) String programId,
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.majorProjectTree(filters(programId, majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/data-support", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dataSupport(
            @RequestParam(value = "programId", required = false) String programId,
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.dataSupport(filters(programId, majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/screen/header", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> screenHeader(
            @RequestParam(value = "programId", required = false) String programId,
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.screenHeader(filters(programId, majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/screen/overview", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> screenOverview(
            @RequestParam(value = "programId", required = false) String programId,
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.screenOverview(filters(programId, majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/screen/execution", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> screenExecution(
            @RequestParam(value = "programId", required = false) String programId,
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.screenExecution(filters(programId, majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    @GetMapping(path = "/screen/risk", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> screenRisk(
            @RequestParam(value = "programId", required = false) String programId,
            @RequestParam(value = "majorProjectId", required = false) String majorProjectId,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "deptId", required = false) String deptId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            HttpServletRequest request) {
        return authorize(request, projectCockpitService.screenRisk(filters(programId, majorProjectId, dateFrom, dateTo, deptId, riskLevel)));
    }

    private ResponseEntity<?> authorize(HttpServletRequest request, Object payload) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }
        return ResponseEntity.ok(payload);
    }

    private ProjectCockpitService.Filters filters(
            String programId,
            String majorProjectId,
            String dateFrom,
            String dateTo,
            String deptId,
            String riskLevel) {
        return new ProjectCockpitService.Filters(
                blankToNull(programId),
                blankToNull(majorProjectId),
                parseDate(dateFrom),
                parseDate(dateTo),
                blankToNull(deptId),
                blankToNull(riskLevel));
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
