package com.yuzhi.dts.platform.web.rest;

import static com.yuzhi.dts.platform.service.catalog.ArchitectureDictionaryWriteGuard.WRITE_EXPRESSION;

import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainApplicationService;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.Status;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.UpdateCommand;
import com.yuzhi.dts.platform.service.modeling.SubjectDomainContract.View;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Strict canonical REST boundary for the modeling subject-domain ledger. */
@RestController
@RequestMapping("/api/modeling/subject-domains")
public class SubjectDomainResource {

    private static final Pattern STRONG_ETAG = Pattern.compile(
        "^\\\"subject-domain:([0-9a-fA-F-]{36}):([1-9][0-9]*):([0-9a-f]{64})\\\"$"
    );

    private final SubjectDomainApplicationService service;
    private final WarehousePlanActorProvider actorProvider;
    private final String serverTenantId;

    public SubjectDomainResource(
        SubjectDomainApplicationService service,
        WarehousePlanActorProvider actorProvider,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String serverTenantId
    ) {
        this.service = service;
        this.actorProvider = actorProvider;
        this.serverTenantId = serverTenantId;
    }

    @PostMapping
    @PreAuthorize(WRITE_EXPRESSION)
    public ResponseEntity<ApiResponse<View>> create(@RequestBody CreateCommand command) {
        CreateResult result = service.create(serverTenantId, actorId(), command);
        View view = result.subjectDomain();
        return ResponseEntity
            .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
            .location(URI.create("/api/modeling/subject-domains/" + view.id()))
            .eTag(SubjectDomainApplicationService.etag(view))
            .body(ApiResponses.ok(view));
    }

    @GetMapping
    public ApiResponse<List<View>> list(
        @RequestParam(required = false) UUID martId,
        @RequestParam(required = false) Status status,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "" + SubjectDomainApplicationService.DEFAULT_LIST_LIMIT) int limit
    ) {
        return ApiResponses.ok(service.list(serverTenantId, martId, status, keyword, offset, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<View>> get(@PathVariable UUID id) {
        return response(service.get(serverTenantId, id));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_EXPRESSION)
    public ResponseEntity<ApiResponse<View>> update(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch,
        @RequestBody UpdateCommand command
    ) {
        return response(service.update(serverTenantId, actorId(), id, expected(id, ifMatch), command));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize(WRITE_EXPRESSION)
    public ResponseEntity<ApiResponse<View>> confirm(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch
    ) {
        return response(service.confirm(serverTenantId, actorId(), id, expected(id, ifMatch)));
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize(WRITE_EXPRESSION)
    public ResponseEntity<ApiResponse<View>> retire(
        @PathVariable UUID id,
        @RequestHeader(value = "If-Match", required = false) String ifMatch
    ) {
        return response(service.retire(serverTenantId, actorId(), id, expected(id, ifMatch)));
    }

    @ExceptionHandler(ModelSpecException.class)
    public ResponseEntity<ApiResponse<Object>> handleModelingError(ModelSpecException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_REQUIRED -> HttpStatus.PRECONDITION_REQUIRED;
        };
        return ResponseEntity
            .status(status)
            .body(new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), exception.details()));
    }

    private ResponseEntity<ApiResponse<View>> response(View view) {
        return ResponseEntity.ok().eTag(SubjectDomainApplicationService.etag(view)).body(ApiResponses.ok(view));
    }

    private static ExpectedVersion expected(UUID id, String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ModelSpecException(
                "SUBJECT_DOMAIN_IF_MATCH_REQUIRED",
                "If-Match is required",
                ModelSpecException.Kind.PRECONDITION_REQUIRED
            );
        }
        Matcher matcher = STRONG_ETAG.matcher(ifMatch.trim());
        if (!matcher.matches() || !id.equals(UUID.fromString(matcher.group(1)))) {
            throw new ModelSpecException(
                "SUBJECT_DOMAIN_IF_MATCH_INVALID",
                "If-Match does not identify this subject domain",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        return new ExpectedVersion(id, Integer.parseInt(matcher.group(2)), matcher.group(3));
    }

    private String actorId() {
        WarehousePlanActorProvider.WarehousePlanActor actor = actorProvider.currentActor();
        return actor == null ? null : actor.ownerId();
    }
}
