package com.yuzhi.dts.platform.config;

import com.yuzhi.dts.common.audit.AuditActionCatalog;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Registers shared audit catalog components from dts-common.
 */
@Configuration
@Import(AuditActionCatalog.class)
public class AuditCatalogConfiguration {}
