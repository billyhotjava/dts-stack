package com.yuzhi.dts.platform.service.catalog;

import java.util.Set;
import java.util.UUID;

/** Public, immutable Catalog boundary for consumers that need domain access facts. */
public interface CatalogDomainAccessReadPort {

    boolean canRead(UUID domainId);

    boolean canMaintain(UUID domainId);

    Set<UUID> visibleDomainIds();

    DomainSnapshot resolve(UUID domainId);

    enum DomainStatus {
        AVAILABLE,
        ARCHIVED,
        FORBIDDEN,
        MISSING,
    }

    record DomainSnapshot(
        UUID id,
        DomainStatus status,
        String name,
        String code,
        String owner,
        String description,
        UUID parentId
    ) {
        public DomainSnapshot(UUID id, DomainStatus status, String name, String code, String owner, String description) {
            this(id, status, name, code, owner, description, null);
        }
        public DomainSnapshot {
            if (status == null) {
                throw new IllegalArgumentException("status is required");
            }
            if ((status == DomainStatus.FORBIDDEN || status == DomainStatus.MISSING) &&
                (name != null || code != null || owner != null || description != null || parentId != null)) {
                throw new IllegalArgumentException("redacted domain snapshots cannot expose metadata");
            }
        }

        public static DomainSnapshot redacted(UUID id, DomainStatus status) {
            return new DomainSnapshot(id, status, null, null, null, null);
        }
    }
}
