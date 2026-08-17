package com.yuzhi.dts.analytics.web.rest;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsAssetAccessRegistrar;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService.AnalysisDto;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService.AnalysisPage;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService.CreateAnalysisCommand;
import com.yuzhi.dts.analytics.service.analysis.AnalysisApplicationService.UpdateAnalysisCommand;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQueryGateway;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQuerySpec;
import com.yuzhi.dts.analytics.service.analysis.AnalysisRequestContext;
import com.yuzhi.dts.analytics.service.publication.AnalysisPublicationService;
import com.yuzhi.dts.analytics.service.publication.AnalysisPublicationService.PublicationCommand;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
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

    public AnalysisResource(
        AnalysisApplicationService analysisService,
        AnalyticsSessionService sessionService,
        AnalyticsAssetAccessRegistrar assetAccessRegistrar,
        AnalysisQueryGateway queryGateway,
        AnalysisPublicationService publicationService
    ) {
        this.analysisService = analysisService;
        this.sessionService = sessionService;
        this.assetAccessRegistrar = assetAccessRegistrar;
        this.queryGateway = queryGateway;
        this.publicationService = publicationService;
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

    @PostMapping("/queries/{queryId}/cancel")
    public ResponseEntity<?> cancelQuery(@PathVariable String queryId, HttpServletRequest request) {
        Optional<AnalyticsUser> actor = actor(request);
        if (actor.isEmpty()) return unauthorized();
        return ResponseEntity.ok(Map.of("queryId", queryId, "cancelled", queryGateway.cancelQuery(queryId, actor.get())));
    }

    private Optional<AnalyticsUser> actor(HttpServletRequest request) {
        return sessionService.resolveUser(request).filter(AnalyticsUser::isActive);
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("errorCode", "ANALYSIS_UNAUTHENTICATED", "message", "Authentication required"));
    }
}
