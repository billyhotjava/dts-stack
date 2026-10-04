package com.yuzhi.dts.platform.service.workbench.dto;

/**
 * Projection row for business-domain visit aggregations (Sprint-15 F1/T06).
 *
 * <p>Emitted by a {@code group by r.bizDomain} JPQL query; {@code bizDomain}
 * may be {@code null} for reports that have no associated domain.
 */
public record DomainAggregateRow(String bizDomain, long visits) {}
