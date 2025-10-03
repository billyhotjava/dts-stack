package com.yuzhi.dts.admin.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrganizationSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(OrganizationSyncService.class);

    private final OrganizationService organizationService;

    public OrganizationSyncService(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostConstruct
    public void initializeDefaults() {
        try {
            organizationService.ensureUnassignedRoot();
        } catch (RuntimeException ex) {
            LOG.warn("Failed to ensure default unassigned organization: {}", ex.getMessage());
        }
    }

    public void ensureUnassignedRoot() {
        organizationService.ensureUnassignedRoot();
    }

    public void syncAll() {
        organizationService.ensureUnassignedRoot();
        organizationService.pushTreeToKeycloak();
    }
}

