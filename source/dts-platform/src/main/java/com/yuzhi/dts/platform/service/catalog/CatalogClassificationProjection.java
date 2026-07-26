package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;

/**
 * One-way compatibility bridge from the canonical classification fact to legacy classification columns.
 *
 * <p>Implementations must be idempotent and must never feed a legacy value back into the canonical snapshot.
 */
public interface CatalogClassificationProjection {
    boolean supports(CatalogClassificationSnapshot snapshot);

    void project(CatalogClassificationSnapshot snapshot);
}
