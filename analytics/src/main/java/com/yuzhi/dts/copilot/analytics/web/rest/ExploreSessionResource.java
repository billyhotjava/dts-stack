package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.ExploreSessionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.copilot.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/explore-session")
public class ExploreSessionResource {

    private final AnalyticsSessionService sessionService;
    private final ExploreSessionService exploreSessionService;

    public ExploreSessionResource(
            AnalyticsSessionService sessionService,
            ExploreSessionService exploreSessionService) {
        this.sessionService = sessionService;
        this.exploreSessionService = exploreSessionService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(
            @RequestParam(name = "includeArchived", required = false, defaultValue = "false") boolean includeArchived,
            @RequestParam(name = "dept", required = false) String dept,
            @RequestParam(name = "projectKey", required = false) String projectKey,
            @RequestParam(name = "limit", required = false, defaultValue = "100") int limit,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        String effectiveDept = dept == null || dept.isBlank() ? PlatformContext.from(request).dept() : dept;
        return ResponseEntity.ok(exploreSessionService.list(
                user.get().getId(),
                user.get().isSuperuser(),
                includeArchived,
                effectiveDept,
                projectKey,
                limit));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        PlatformContext context = PlatformContext.from(request);
        return ResponseEntity.ok(exploreSessionService.create(body, user.get().getId(), context.dept(), null));
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(exploreSessionService.get(id, user.get().getId(), user.get().isSuperuser()));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(
            @PathVariable("id") long id, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(exploreSessionService.update(id, body, user.get().getId(), user.get().isSuperuser()));
    }

    @PostMapping(path = "/{id}/steps", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> appendStep(
            @PathVariable("id") long id, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(exploreSessionService.appendStep(id, body, user.get().getId(), user.get().isSuperuser()));
    }

    @PostMapping(path = "/{id}/replay", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> replayStep(
            @PathVariable("id") long id, @RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        int stepIndex = body == null ? 0 : body.path("stepIndex").asInt(0);
        return ResponseEntity.ok(exploreSessionService.replayStep(id, stepIndex, user.get().getId(), user.get().isSuperuser()));
    }

    @PostMapping(path = "/{id}/archive", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> archive(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(exploreSessionService.archive(id, user.get().getId(), user.get().isSuperuser()));
    }

    @PostMapping(path = "/{id}/clone", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cloneSession(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(exploreSessionService.cloneSession(id, user.get().getId(), user.get().isSuperuser()));
    }

    @PostMapping(path = "/{id}/public_link", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createPublicLink(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        PlatformContext context = PlatformContext.from(request);
        String uuid = exploreSessionService.getOrCreatePublicLink(
                id, user.get().getId(), user.get().isSuperuser(), context.dept(), context.classification());
        return ResponseEntity.ok(java.util.Map.of("uuid", uuid));
    }

    @DeleteMapping(path = "/{id}/public_link")
    public ResponseEntity<?> deletePublicLink(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        exploreSessionService.deletePublicLink(id, user.get().getId(), user.get().isSuperuser());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/public/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> publicGet(@PathVariable("uuid") String uuid, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity.ok(exploreSessionService.getByPublicUuid(uuid));
    }
}
