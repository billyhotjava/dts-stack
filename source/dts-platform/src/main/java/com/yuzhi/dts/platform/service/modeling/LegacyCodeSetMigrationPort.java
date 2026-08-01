package com.yuzhi.dts.platform.service.modeling;

import java.util.List;
import java.util.UUID;

/** Owner-side mutation contract used only to retire legacy inline code sets. */
public interface LegacyCodeSetMigrationPort {

    List<LegacyCodeSetCandidate> findCandidates();

    /**
     * Atomically claims the current inline snapshot and replaces it with the governed code type.
     * Dependent directory/value writes must run in the same transaction after this succeeds.
     */
    boolean replaceInlineCodeSet(UUID standardId, String expectedInlineCodeSet, String codeTypeCode);

    record LegacyCodeSetCandidate(
        UUID id,
        String code,
        String name,
        String domain,
        String dataType,
        String currentVersion,
        String inlineCodeSet
    ) {}
}
