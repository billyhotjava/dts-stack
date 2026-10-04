package com.yuzhi.dts.analytics.web.rest;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsAssetAccessRegistrar;
import com.yuzhi.dts.analytics.service.AnalyticsClassificationClient;
import com.yuzhi.dts.analytics.service.AnalyticsConsumerClassificationService;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.DatasetQueryService;
import com.yuzhi.dts.analytics.service.QueryExportService;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService.AnalysisDto;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService.AnalysisPage;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService.CreateAnalysisCommand;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService.UpdateAnalysisCommand;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQueryGateway;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQueryGateway.AnalysisQueryResult;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQuerySpec;
import com.yuzhi.dts.analytics.service.analysis.AnalysisRequestContext;
import com.yuzhi.dts.analytics.service.publication.AnalysisPublicationService;
import com.yuzhi.dts.analytics.service.publication.AnalysisPublicationService.PublicationCommand;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisResource {

    private final AnalysisApplicationService analysisService;
    private final AnalyticsSessionService sessionService;
    private final AnalyticsAssetAccessRegistrar assetAccessRegistrar;
    private final AnalysisQueryGateway queryGateway;
    private final AnalysisPublicationService publicationService;
    private final QueryExportService queryExportService;
    private final AnalyticsConsumerClassificationService classificationService;

    public AnalysisResource(
        AnalysisApplicationService analysisService,
        AnalyticsSessionService sessionService,
        AnalyticsAssetAccessRegistrar assetAccessRegistrar,
        AnalysisQueryGateway queryGateway,
        AnalysisPublicationService publicationService,
        QueryExportService queryExportService,
        AnalyticsConsumerClassificationService classificationService
    ) {
        this.analysisService = analysisService;
        this.sessionService = sessionService;
        this.assetAccessRegistrar = assetAccessRegistrar;
        this.queryGateway = queryGateway;
        this.publicationService = publicationService;
        this.queryExportService = queryExportService;
        this.classificationService = classificationService;
    }

    @GetMapping
    public ResponseEntity<?> list(
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size,
        HttpServletRequest request
    ) {
        Optional<AnalyticsUser> actor = actor(request);
        if (actor.isEmpty()) return unauthorized();
        AnalysisPage response = analysisService.list(page, size, actor.get());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> create(
        @RequestBody CreateAnalysisCommand command,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        HttpServletRequest request
    ) {
        Optional<AnalyticsUser> actor = actor(request);
        if (actor.isEmpty()) return unauthorized();
        AnalysisDto response = analysisService.create(command, actor.get(), idempotencyKey);
        assetAccessRegistrar.register("CARD", response.id(), actor.get(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable long id, HttpServletRequest request) {
        Optional<AnalyticsUser> actor = actor(request);
        return actor.<ResponseEntity<?>>map(user -> ResponseEntity.ok(analysisService.get(id, user))).orElseGet(this::unauthorized);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
        @PathVariable long id,
        @RequestBody UpdateAnalysisCommand command,
        HttpServletRequest request
    ) {
        Optional<AnalyticsUser> actor = actor(request);
        return actor.<ResponseEntity<?>>map(user -> ResponseEntity.ok(analysisService.update(id, command, user))).orElseGet(this::unauthorized);
    }

    @PostMapping("/{id}/copy")
    public ResponseEntity<?> copy(@PathVariable long id, HttpServletRequest request) {
        Optional<AnalyticsUser> actor = actor(request);
        if (actor.isEmpty()) return unauthorized();
        AnalysisDto response = analysisService.copy(id, actor.get());
        assetAccessRegistrar.register("CARD", response.id(), actor.get(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<?> archive(@PathVariable long id, HttpServletRequest request) {
        Optional<AnalyticsUser> actor = actor(request);
        return actor.<ResponseEntity<?>>map(user -> ResponseEntity.ok(analysisService.archive(id, user))).orElseGet(this::unauthorized);
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restore(@PathVariable long id, HttpServletRequest request) {
        Optional<AnalyticsUser> actor = actor(request);
        return actor.<ResponseEntity<?>>map(user -> ResponseEntity.ok(analysisService.restore(id, user))).orElseGet(this::unauthorized);
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<?> validate(
        @PathVariable long id,
        @RequestBody(required = false) PublicationCommand command,
        HttpServletRequest request
    ) {
        Optional<AnalyticsUser> actor = actor(request);
        return actor
            .<ResponseEntity<?>>map(user -> ResponseEntity.ok(publicationService.validate(id, user, command)))
            .orElseGet(this::unauthorized);
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<?> publish(
        @PathVariable long id,
        @RequestBody PublicationCommand command,
        HttpServletRequest request
    ) {
        Optional<AnalyticsUser> actor = actor(request);
        return actor
            .<ResponseEntity<?>>map(user -> ResponseEntity.ok(publicationService.publish(id, user, command)))
            .orElseGet(this::unauthorized);
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<?> versions(@PathVariable long id, HttpServletRequest request) {
        Optional<AnalyticsUser> actor = actor(request);
        return actor
            .<ResponseEntity<?>>map(user -> ResponseEntity.ok(publicationService.versions(id, user)))
            .orElseGet(this::unauthorized);
    }

    @PostMapping("/{id}/versions/{revisionId}/draft")
    public ResponseEntity<?> createDraftFromVersion(
        @PathVariable long id,
        @PathVariable long revisionId,
        HttpServletRequest request
    ) {
        Optional<AnalyticsUser> actor = actor(request);
        if (actor.isEmpty()) return unauthorized();
        AnalysisDto response = publicationService.createDraftFromVersion(id, revisionId, actor.get());
        assetAccessRegistrar.register("CARD", response.id(), actor.get(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/preview")
    public ResponseEntity<?> preview(
        @RequestBody AnalysisQuerySpec querySpec,
        HttpServletRequest request
    ) {
        Optional<AnalyticsUser> actor = actor(request);
        return actor
            .<ResponseEntity<?>>map(user -> ResponseEntity.ok(queryGateway.preview(user, querySpec, AnalysisRequestContext.from(request))))
            .orElseGet(this::unauthorized);
    }

    @PostMapping("/{id}/query")
    public ResponseEntity<?> query(@PathVariable long id, HttpServletRequest request) {
        Optional<AnalyticsUser> actor = actor(request);
        if (actor.isEmpty()) return unauthorized();
        AnalysisDto analysis = analysisService.get(id, actor.get());
        return ResponseEntity.ok(queryGateway.preview(actor.get(), analysis.querySpec(), AnalysisRequestContext.from(request)));
    }

    @PostMapping(path = "/{id}/query/csv")
    public void exportCsv(
        @PathVariable long id,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        exportAnalysis(id, request, response, QueryExportService.ExportFormat.CSV);
    }

    @PostMapping(path = "/{id}/query/xlsx")
    public void exportXlsx(
        @PathVariable long id,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        exportAnalysis(id, request, response, QueryExportService.ExportFormat.EXCEL);
    }

    private void exportAnalysis(
        long id,
        HttpServletRequest request,
        HttpServletResponse response,
        QueryExportService.ExportFormat format
    ) throws IOException {
        Optional<AnalyticsUser> actor = actor(request);
        if (actor.isEmpty()) {
            response.setStatus(401);
            response.getWriter().write("Authentication required");
            return;
        }

        AnalysisDto analysis = analysisService.get(id, actor.get());
        String baseName = sanitizeFilename(analysis.name());
        String filename = baseName + queryExportService.getFileExtension(format);
        String fileSubjectKey = "analytics-analysis-export:" + UUID.randomUUID() + ":" + filename;
        AnalyticsClassificationClient.ExportSeal exportSeal;
        try {
            exportSeal = classificationService.sealCardExport(
                id,
                fileSubjectKey,
                PlatformContext.from(request).classification()
            );
        } catch (AnalyticsConsumerClassificationService.PersonnelClassificationDeniedException denied) {
            response.setStatus(403);
            response.getWriter().write(denied.getMessage());
            return;
        } catch (RuntimeException missingClassification) {
            response.setStatus(409);
            response.getWriter().write("Analysis classification is missing or awaiting recomputation");
            return;
        }

        AnalysisQueryResult queryResult = queryGateway.preview(
            actor.get(),
            analysis.querySpec(),
            AnalysisRequestContext.from(request)
        );
        DatasetQueryService.DatasetResult result = new DatasetQueryService.DatasetResult(
            queryResult.rows(),
            queryResult.columns(),
            queryResult.columns(),
            "UTC"
        );

        response.setContentType(queryExportService.getContentType(format));
        String asciiFilename = "analysis-" + id + queryExportService.getFileExtension(format);
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader(
            "Content-Disposition",
            "attachment; filename=\"" + asciiFilename + "\"; filename*=UTF-8''" + encodedFilename
        );
        response.setHeader("X-DTS-Classification", exportSeal.effectiveLevel());
        response.setHeader("X-DTS-Classification-Snapshot", exportSeal.snapshotId());

        QueryExportService.ExportOptions options = format == QueryExportService.ExportFormat.CSV
            ? QueryExportService.ExportOptions.forCsv()
            : QueryExportService.ExportOptions.forExcel().withSheetName(sheetName(baseName));
        if (format == QueryExportService.ExportFormat.CSV) {
            queryExportService.exportToCsv(result, response.getOutputStream(), options);
        } else {
            queryExportService.exportToExcel(result, response.getOutputStream(), options);
        }
    }

    @PostMapping("/queries/{queryId}/cancel")
    public ResponseEntity<?> cancelQuery(@PathVariable String queryId, HttpServletRequest request) {
        Optional<AnalyticsUser> actor = actor(request);
        if (actor.isEmpty()) return unauthorized();
        return ResponseEntity.ok(Map.of("queryId", queryId, "cancelled", queryGateway.cancelQuery(queryId, actor.get())));
    }

    private Optional<AnalyticsUser> actor(HttpServletRequest request) {
        return sessionService.resolveUser(request).filter(AnalyticsUser::isActive);
    }

    private String sanitizeFilename(String value) {
        if (value == null || value.isBlank()) return "analysis";
        String sanitized = value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (sanitized.isEmpty()) return "analysis";
        return sanitized.length() > 120 ? sanitized.substring(0, 120) : sanitized;
    }

    private String sheetName(String value) {
        String sanitized = value.replaceAll("[\\\\/*?:\\[\\]]", "_");
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("errorCode", "ANALYSIS_UNAUTHENTICATED", "message", "Authentication required"));
    }
}
