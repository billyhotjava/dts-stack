package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomainLifecycleStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Stable, immutable read boundary for consumers of the platform domain dictionary. */
public interface CatalogDomainDictionaryReadPort {

    boolean existsByCode(String code);

    List<DomainRecord> findByCodeCandidates(Collection<String> codes);

    record DomainRecord(
        UUID id,
        String code,
        String name,
        String owner,
        UUID parentId,
        CatalogDomainLifecycleStatus status,
        Instant version
    ) {}
}
