package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun;
import com.yuzhi.dts.platform.repository.modeling.StandardPackageImportRunRepository;
import com.yuzhi.dts.platform.service.modeling.StandardPackageInstallLedgerPort.AppliedPackageCommand;
import com.yuzhi.dts.platform.service.modeling.StandardPackageInstallLedgerPort.AppliedPackageRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class StandardPackageInstallLedgerService implements StandardPackageInstallLedgerPort {

    private final StandardPackageImportRunRepository runs;

    public StandardPackageInstallLedgerService(StandardPackageImportRunRepository runs) {
        this.runs = runs;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppliedPackageRecord> findAppliedBySource(String source) {
        if (!StringUtils.hasText(source)) return List.of();
        return runs
            .findByStatusOrderByCreatedDateDesc(StandardPackageApplyService.STATUS_APPLIED)
            .stream()
            .filter(run -> source.equals(run.getSource()))
            .map(run -> new AppliedPackageRecord(run.getId(), run.getPreviewJson()))
            .toList();
    }

    @Override
    public void recordApplied(AppliedPackageCommand command) {
        if (
            command == null ||
            !StringUtils.hasText(command.packageName()) ||
            !StringUtils.hasText(command.source()) ||
            !StringUtils.hasText(command.previewJson()) ||
            !StringUtils.hasText(command.payloadJson()) ||
            !StringUtils.hasText(command.actor())
        ) {
            throw new IllegalArgumentException("standard package ledger command is incomplete");
        }
        Instant occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        StandardPackageImportRun run = new StandardPackageImportRun();
        run.setPackageName(command.packageName());
        run.setSource(command.source());
        run.setStatus(StandardPackageApplyService.STATUS_APPLIED);
        run.setSummary(command.summary());
        run.setPreviewJson(command.previewJson());
        run.setPayloadJson(command.payloadJson());
        run.setCreatedBy(command.actor());
        run.setCreatedDate(occurredAt);
        run.setLastModifiedBy(command.actor());
        run.setLastModifiedDate(occurredAt);
        runs.save(run);
    }
}
