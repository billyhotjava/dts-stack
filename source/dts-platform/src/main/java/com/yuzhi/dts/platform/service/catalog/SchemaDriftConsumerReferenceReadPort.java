package com.yuzhi.dts.platform.service.catalog;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Current ModelSpec field references used to refine a structural schema-drift decision. */
@FunctionalInterface
public interface SchemaDriftConsumerReferenceReadPort {

    Optional<Set<String>> findCurrentReferencedFields(
        UUID catalogTableId,
        UUID connectionId,
        String namespace,
        String objectName
    );
}
