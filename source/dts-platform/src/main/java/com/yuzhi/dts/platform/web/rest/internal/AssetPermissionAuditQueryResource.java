package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit;
import com.yuzhi.dts.platform.repository.permission.AssetPermissionAuditRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/asset-permission/audit")
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "')")
public class AssetPermissionAuditQueryResource {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 200;

    private final AssetPermissionAuditRepository auditRepository;

    public AssetPermissionAuditQueryResource(AssetPermissionAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @GetMapping("/denied")
    public ResponseEntity<List<DeniedAuditResponse>> denied(
        @RequestParam(required = false) String reasonCode,
        @RequestParam(required = false) String assetId,
        @RequestParam(required = false) Instant since,
        @RequestParam(required = false) Integer limit
    ) {
        PageRequest pageable = PageRequest.of(0, normalizeLimit(limit));
        List<DeniedAuditResponse> rows = auditRepository
            .findDeniedAudits(trimToNull(reasonCode), trimToNull(assetId), since, pageable)
            .getContent()
            .stream()
            .map(DeniedAuditResponse::from)
            .toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping(value = "/denied.csv", produces = "text/csv")
    public ResponseEntity<String> deniedCsv(
        @RequestParam(required = false) String reasonCode,
        @RequestParam(required = false) String assetId,
        @RequestParam(required = false) Instant since,
        @RequestParam(required = false) Integer limit
    ) {
        PageRequest pageable = PageRequest.of(0, normalizeLimit(limit));
        List<AssetPermissionAudit> rows = auditRepository
            .findDeniedAudits(trimToNull(reasonCode), trimToNull(assetId), since, pageable)
            .getContent();
        return ResponseEntity
            .ok()
            .contentType(new MediaType("text", "csv"))
            .body(toCsv(rows));
    }

    private static String toCsv(List<AssetPermissionAudit> rows) {
        StringBuilder csv = new StringBuilder("actor,assetType,assetId,permission,operator,reasonCode,reasonDetail,deniedAt\n");
        for (AssetPermissionAudit row : rows) {
            csv
                .append(csv(row.getTargetUser()))
                .append(',')
                .append(csv(row.getAssetType()))
                .append(',')
                .append(csv(row.getAssetId()))
                .append(',')
                .append(csv(row.getPermission()))
                .append(',')
                .append(csv(row.getOperator()))
                .append(',')
                .append(csv(row.getReasonCode()))
                .append(',')
                .append(quotedCsv(row.getReasonDetail()))
                .append(',')
                .append(csv(row.getCreatedDate() == null ? null : row.getCreatedDate().toString()))
                .append('\n');
        }
        return csv.toString();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String quotedCsv(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record DeniedAuditResponse(
        Long id,
        String action,
        String assetType,
        String assetId,
        String targetUser,
        String permission,
        String operator,
        String reasonCode,
        String reasonDetail,
        Instant createdDate
    ) {
        static DeniedAuditResponse from(AssetPermissionAudit audit) {
            return new DeniedAuditResponse(
                audit.getId(),
                audit.getAction(),
                audit.getAssetType(),
                audit.getAssetId(),
                audit.getTargetUser(),
                audit.getPermission(),
                audit.getOperator(),
                audit.getReasonCode(),
                audit.getReasonDetail(),
                audit.getCreatedDate()
            );
        }
    }
}
