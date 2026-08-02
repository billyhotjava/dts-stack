package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovIssueTicket;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.service.governance.request.IssueTicketUpsertRequest;
import jakarta.persistence.EntityNotFoundException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Enforces object-level identity rules for client-created issue sources. */
@Service
public class IssueSourcePolicy {

    private static final String QUALITY_RUN = "QUALITY_RUN";

    private final GovQualityRunRepository runRepository;
    private final QualityDatasetReadGuard datasetReadGuard;

    public IssueSourcePolicy(
        GovQualityRunRepository runRepository,
        QualityDatasetReadGuard datasetReadGuard
    ) {
        this.runRepository = runRepository;
        this.datasetReadGuard = datasetReadGuard;
    }

    public void validateClientCreate(IssueTicketUpsertRequest request, String activeDeptHeader) {
        if (request == null) {
            return;
        }
        String sourceType = normalizeSourceType(request.getSourceType());
        UUID sourceId = request.getSourceId();
        if (sourceType == null && sourceId == null) {
            return;
        }
        if (sourceType == null || sourceId == null) {
            throw new IllegalArgumentException("问题来源类型和来源 ID 必须同时提供");
        }
        if (!QUALITY_RUN.equals(sourceType)) {
            throw new AccessDeniedException("客户端无权绑定该系统问题来源");
        }

        GovQualityRun run = runRepository.findById(sourceId).orElseThrow(EntityNotFoundException::new);
        UUID sourceDatasetId = run.getDatasetId();
        if (sourceDatasetId == null) {
            throw new IllegalArgumentException("质量运行未绑定数据资产");
        }
        datasetReadGuard.requireReadable(sourceDatasetId, activeDeptHeader);
        if (request.getDatasetId() != null && !Objects.equals(request.getDatasetId(), sourceDatasetId)) {
            throw new AccessDeniedException("问题来源与数据资产不一致");
        }
        request.setSourceType(QUALITY_RUN);
        request.setDatasetId(sourceDatasetId);
    }

    public void assertImmutable(
        GovIssueTicket ticket,
        IssueTicketUpsertRequest request,
        String activeDeptHeader
    ) {
        if (ticket == null || request == null) {
            return;
        }
        String requestedType = normalizeSourceType(request.getSourceType());
        UUID requestedId = request.getSourceId();
        String currentType = normalizeSourceType(ticket.getSourceType());
        if (
            (requestedType != null || requestedId != null) &&
            (requestedType == null ||
                requestedId == null ||
                !Objects.equals(currentType, requestedType) ||
                !Objects.equals(ticket.getSourceRefId(), requestedId))
        ) {
            throw new IllegalArgumentException("问题来源创建后不可修改");
        }
        if (QUALITY_RUN.equals(currentType) && ticket.getSourceRefId() != null) {
            GovQualityRun run = runRepository
                .findById(ticket.getSourceRefId())
                .orElseThrow(EntityNotFoundException::new);
            UUID sourceDatasetId = run.getDatasetId();
            if (sourceDatasetId == null) {
                throw new IllegalArgumentException("质量运行未绑定数据资产");
            }
            datasetReadGuard.requireReadable(sourceDatasetId, activeDeptHeader);
            if (request.getDatasetId() != null && !Objects.equals(request.getDatasetId(), sourceDatasetId)) {
                throw new AccessDeniedException("问题来源与数据资产不一致");
            }
            request.setDatasetId(sourceDatasetId);
        } else if (request.getDatasetId() == null) {
            request.setDatasetId(ticket.getDatasetId());
        }
    }

    private String normalizeSourceType(String value) {
        String normalized = StringUtils.trimToNull(value);
        return normalized != null ? normalized.toUpperCase(Locale.ROOT) : null;
    }
}
