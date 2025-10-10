package com.yuzhi.dts.admin.config;

import com.yuzhi.dts.common.audit.AuditActionCatalog;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Registers the shared audit catalog from dts-common for admin services.
 */
@Configuration
@Import(AuditActionCatalog.class)
public class AuditCatalogConfiguration {}
