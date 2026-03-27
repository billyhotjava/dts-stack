package com.yuzhi.dts.platform.web.rest;

/**
 * @deprecated This monolithic controller has been split into domain-focused controllers:
 * <ul>
 *   <li>{@link com.yuzhi.dts.platform.web.rest.catalog.CatalogDomainResource} — domain CRUD</li>
 *   <li>{@link com.yuzhi.dts.platform.web.rest.catalog.CatalogDatasetResource} — dataset CRUD, config, summary, metadata</li>
 *   <li>{@link com.yuzhi.dts.platform.web.rest.catalog.CatalogSecurityResource} — security mapping, grants</li>
 *   <li>{@link com.yuzhi.dts.platform.web.rest.catalog.CatalogMaskingResource} — masking rules, classification mapping, linkage</li>
 *   <li>{@link com.yuzhi.dts.platform.web.rest.catalog.CatalogGovernanceResource} — governance health, reconciliation</li>
 *   <li>{@link com.yuzhi.dts.platform.web.rest.catalog.CatalogSchemaResource} — tables, columns, row filters</li>
 * </ul>
 * Shared helper methods are in {@link com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper}.
 */
@Deprecated(since = "v2.2.2", forRemoval = true)
public class CatalogResource {
    // Intentionally empty — all endpoints have been moved to the catalog sub-package.
}
