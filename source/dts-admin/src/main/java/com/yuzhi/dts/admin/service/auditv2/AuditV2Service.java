package com.yuzhi.dts.admin.service.auditv2;

import com.yuzhi.dts.admin.domain.audit.AuditEntry;
import com.yuzhi.dts.admin.service.auditv2.ButtonCodes;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuditV2Service {

    private final AuditRecorder recorder;
    private final AuditButtonRegistry buttonRegistry;

    public AuditV2Service(AuditRecorder recorder, AuditButtonRegistry buttonRegistry) {
        this.recorder = recorder;
        this.buttonRegistry = buttonRegistry;
    }

    public AuditEntry record(AuditActionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        AuditButtonMetadata metadata = buttonRegistry.resolve(request.buttonCode()).orElse(null);

        String moduleKey = firstNonBlank(request.moduleKeyOverride(), metadata != null ? metadata.moduleKey() : null);
        if (!StringUtils.hasText(moduleKey)) {
            throw new IllegalStateException("Missing moduleKey for button " + request.buttonCode());
        }
        AuditOperationKind operationKind = request.operationKindOverride() != null
            ? request.operationKindOverride()
            : metadata != null ? metadata.operationKind() : AuditOperationKind.OTHER;
        boolean allowEmptyTargets = request.allowEmptyTargets() || (metadata != null && metadata.allowEmptyTargets());

        AuditRecorder.AuditBuilder builder = recorder
            .start(request.actorId())
            .sourceSystem("admin")
            .module(moduleKey)
            .actorName(request.actorName())
            .actorRoles(request.actorRoles())
            .result(Optional.ofNullable(request.result()).orElse(AuditResultStatus.SUCCESS))
            .changeRequestRef(request.changeRequestRef())
            .buttonCode(request.buttonCode());

        if (allowEmptyTargets) {
            builder.allowEmptyTargets();
        }

        if (request.occurredAt() != null) {
            builder.occurredAt(request.occurredAt());
        }
        String moduleName = firstNonBlank(request.moduleNameOverride(), metadata != null ? metadata.moduleName() : null);
        if (StringUtils.hasText(moduleName)) {
            builder.moduleName(moduleName);
        }
        String operationCode = firstNonBlank(request.operationCodeOverride(), metadata != null ? metadata.operationCode() : null);
        String operationName = firstNonBlank(request.operationNameOverride(), metadata != null ? metadata.operationName() : null);
        builder.operation(operationCode, operationName, operationKind);

        String summary = firstNonBlank(request.summary(), metadata != null ? metadata.operationName() : null);
        if (StringUtils.hasText(summary)) {
            builder.summary(summary);
        }

        builder.client(request.clientIp(), request.clientAgent()).request(request.requestUri(), request.httpMethod());

        request.metadata().forEach(builder::metadata);
        request.attributes().forEach(builder::extraAttribute);

        for (AuditActionRequest.AuditTarget target : request.targets()) {
            builder.target(target.table(), target.id(), target.label());
        }
        if (!allowEmptyTargets) {
            // builder currently enforces emptiness unless allowEmptyTargets
        }

        List<AuditActionRequest.AuditDetail> details = request.details();
        for (AuditActionRequest.AuditDetail detail : details) {
            builder.detail(detail.key(), detail.value());
        }

        if (!allowEmptyTargets) {
            // Ensure at least one target for non-query operations
            if (operationKind != AuditOperationKind.QUERY && request.targets().isEmpty()) {
                throw new IllegalStateException("Targets required for button " + request.buttonCode());
            }
        }

        return builder.emit();
    }

    private String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        if (StringUtils.hasText(b)) {
            return b.trim();
        }
        return null;
    }
}
