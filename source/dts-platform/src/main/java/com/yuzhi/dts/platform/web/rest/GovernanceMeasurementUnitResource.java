package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitApplicationService;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.ExpectedVersion;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitCommand;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitView;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.ReferenceImpact;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST boundary for versioned measurement-unit ownership. */
@RestController
@RequestMapping("/api/governance/measurement-units")
public class GovernanceMeasurementUnitResource {

    private static final String GOVERNANCE_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).GOVERNANCE_MAINTAINERS)";
    private static final Pattern STRONG_ETAG = Pattern.compile(
        "^\\\"measurement-unit:([0-9a-fA-F-]{36}):([1-9][0-9]*):([0-9a-f]{64})\\\"$"
    );

    private final MeasurementUnitApplicationService service;
    private final AuditService audit;

    public GovernanceMeasurementUnitResource(MeasurementUnitApplicationService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<MeasurementUnitView>> list() {
        List<MeasurementUnitView> result = service.list();
        audit.auditAction(
            "GOV_MEASUREMENT_UNIT_LIST",
            AuditStage.SUCCESS,
            "LIST",
            Map.of("summary", "查看度量单位列表", "count", result.size())
        );
        return ApiResponses.ok(result);
    }

    @PostMapping
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<MeasurementUnitView>> create(@RequestBody MeasurementUnitCommand command) {
        MeasurementUnitView result = service.create(actorId(), command);
        audit.auditAction(
            "GOV_MEASUREMENT_UNIT_CREATE",
            AuditStage.SUCCESS,
            result.id().toString(),
            auditPayload("创建度量单位", result)
        );
        return ResponseEntity.created(URI.create("/api/governance/measurement-units/" + result.id()))
            .header(HttpHeaders.ETAG, etag(result))
            .body(ApiResponses.ok(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MeasurementUnitView>> get(@PathVariable UUID id) {
        MeasurementUnitView result = service.get(id);
        audit.auditAction(
            "GOV_MEASUREMENT_UNIT_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            auditPayload("查看度量单位", result)
        );
        return ResponseEntity.ok().header(HttpHeaders.ETAG, etag(result)).body(ApiResponses.ok(result));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<MeasurementUnitView>> versions(@PathVariable UUID id) {
        List<MeasurementUnitView> result = service.versions(id);
        audit.auditAction(
            "GOV_MEASUREMENT_UNIT_VERSION_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看度量单位版本", "count", result.size())
        );
        return ApiResponses.ok(result);
    }

    @GetMapping("/{id}/references")
    public ApiResponse<ReferenceImpact> references(@PathVariable UUID id) {
        ReferenceImpact result = service.references(id);
        audit.auditAction(
            "GOV_MEASUREMENT_UNIT_REFERENCE_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of(
                "summary",
                "查看度量单位引用",
                "totalReferences",
                result.totalReferences(),
                "restrictedReferences",
                result.restrictedReferences()
            )
        );
        return ApiResponses.ok(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<MeasurementUnitView>> update(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
        @RequestBody MeasurementUnitCommand command
    ) {
        ExpectedVersion expected = parseExpected(ifMatch);
        requireMatchingResource(id, expected);
        MeasurementUnitView result = service.update(actorId(), id, expected, command);
        audit.auditAction(
            "GOV_MEASUREMENT_UNIT_UPDATE",
            AuditStage.SUCCESS,
            id.toString(),
            auditPayload("更新度量单位", result)
        );
        return ResponseEntity.ok().header(HttpHeaders.ETAG, etag(result)).body(ApiResponses.ok(result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(GOVERNANCE_MAINTAINER_EXPRESSION)
    public ResponseEntity<ApiResponse<MeasurementUnitView>> deactivate(
        @PathVariable UUID id,
        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch
    ) {
        ExpectedVersion expected = parseExpected(ifMatch);
        requireMatchingResource(id, expected);
        MeasurementUnitView result = service.deactivate(actorId(), id, expected);
        audit.auditAction(
            "GOV_MEASUREMENT_UNIT_DEACTIVATE",
            AuditStage.SUCCESS,
            id.toString(),
            auditPayload("停用度量单位", result)
        );
        return ResponseEntity.ok().header(HttpHeaders.ETAG, etag(result)).body(ApiResponses.ok(result));
    }

    @ExceptionHandler(MeasurementUnitException.class)
    public ResponseEntity<ApiResponse<Object>> handleMeasurementUnitError(MeasurementUnitException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_REQUIRED -> HttpStatus.PRECONDITION_REQUIRED;
        };
        return ResponseEntity.status(status)
            .body(
                new ApiResponse<>(
                    ResultStatus.ERROR.getCode(),
                    exception.getMessage(),
                    exception.code(),
                    exception.details()
                )
            );
    }

    private static ExpectedVersion parseExpected(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new MeasurementUnitException(
                "MEASUREMENT_UNIT_IF_MATCH_REQUIRED",
                "A strong If-Match precondition is required",
                MeasurementUnitException.Kind.PRECONDITION_REQUIRED
            );
        }
        Matcher matcher = STRONG_ETAG.matcher(ifMatch.trim());
        if (!matcher.matches()) throw invalidIfMatch();
        try {
            return new ExpectedVersion(
                UUID.fromString(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                matcher.group(3)
            );
        } catch (IllegalArgumentException exception) {
            throw invalidIfMatch();
        }
    }

    private static void requireMatchingResource(UUID id, ExpectedVersion expected) {
        if (!id.equals(expected.unitId())) throw invalidIfMatch();
    }

    private static MeasurementUnitException invalidIfMatch() {
        return new MeasurementUnitException(
            "MEASUREMENT_UNIT_IF_MATCH_INVALID",
            "If-Match must be a strong measurement-unit ETag",
            MeasurementUnitException.Kind.BAD_REQUEST
        );
    }

    private static String etag(MeasurementUnitView view) {
        return "\"measurement-unit:" + view.id() + ":" + view.version() + ":" + view.checksum() + "\"";
    }

    private static Map<String, Object> auditPayload(String summary, MeasurementUnitView view) {
        return Map.of("summary", summary, "code", view.code(), "version", view.version());
    }

    private static String actorId() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }
}
