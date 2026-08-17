package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsReportRegistrationOutbox;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsReportRegistrationOutboxRepository
    extends JpaRepository<AnalyticsReportRegistrationOutbox, UUID> {

    long countByStatusIn(List<String> statuses);

    Optional<AnalyticsReportRegistrationOutbox> findByAggregateTypeAndAggregateIdAndAssetVersionAndEventType(
        String aggregateType,
        Long aggregateId,
        Long assetVersion,
        String eventType
    );

    List<AnalyticsReportRegistrationOutbox> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
        List<String> statuses,
        Instant nextAttemptAt
    );

    Optional<AnalyticsReportRegistrationOutbox> findFirstByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
        String aggregateType,
        Long aggregateId
    );

    Optional<AnalyticsReportRegistrationOutbox> findFirstByAggregateTypeAndAggregateIdAndAssetVersionOrderByCreatedAtDesc(
        String aggregateType,
        Long aggregateId,
        Long assetVersion
    );
}
