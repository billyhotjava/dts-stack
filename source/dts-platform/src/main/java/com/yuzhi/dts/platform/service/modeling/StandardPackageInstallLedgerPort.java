package com.yuzhi.dts.platform.service.modeling;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public ledger contract for consumers that install reusable standard packages. */
public interface StandardPackageInstallLedgerPort {

    List<AppliedPackageRecord> findAppliedBySource(String source);

    void recordApplied(AppliedPackageCommand command);

    record AppliedPackageRecord(UUID runId, String previewJson) {}

    record AppliedPackageCommand(
        String packageName,
        String source,
        String summary,
        String previewJson,
        String payloadJson,
        String actor,
        Instant occurredAt
    ) {}
}
