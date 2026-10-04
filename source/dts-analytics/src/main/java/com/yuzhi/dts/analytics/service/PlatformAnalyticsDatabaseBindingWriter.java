package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.service.PlatformAnalyticsDatabaseRegistrationService.AnalysisRegistrationException;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Isolates a unique-binding insert so a losing transaction can safely re-read the winner. */
@Service
class PlatformAnalyticsDatabaseBindingWriter {

    private final AnalyticsDatabaseRepository databaseRepository;

    PlatformAnalyticsDatabaseBindingWriter(AnalyticsDatabaseRepository databaseRepository) {
        this.databaseRepository = databaseRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AnalyticsDatabase insert(AnalyticsDatabase database) {
        return databaseRepository.saveAndFlush(database);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AnalyticsDatabase adoptLegacy(Long id, String tenantId, java.util.UUID platformDataSourceId, String expectedDetails) {
        AnalyticsDatabase current = databaseRepository.findByIdForBindingUpdate(id)
            .orElseThrow(() -> new AnalysisRegistrationException("ANALYSIS_REGISTRATION_CONFLICT"));
        if (tenantId.equals(current.getTenantId()) && platformDataSourceId.equals(current.getPlatformDataSourceId())) return current;
        if (current.getTenantId() != null || !java.util.Objects.equals(expectedDetails, current.getDetailsJson())) {
            throw new AnalysisRegistrationException("ANALYSIS_REGISTRATION_CONFLICT");
        }
        current.setTenantId(tenantId);
        current.setPlatformDataSourceId(platformDataSourceId);
        return databaseRepository.saveAndFlush(current);
    }
}
