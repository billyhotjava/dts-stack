package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovIssueTicket;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface GovIssueTicketRepository extends JpaRepository<GovIssueTicket, UUID>, JpaSpecificationExecutor<GovIssueTicket> {
    List<GovIssueTicket> findByStatusInOrderByCreatedDateDesc(List<String> statuses);
    List<GovIssueTicket> findTop100ByDatasetIdOrderByCreatedDateDesc(UUID datasetId);
    List<GovIssueTicket> findByCreatedDateAfterOrderByCreatedDateAsc(Instant since);
    List<GovIssueTicket> findByAssignedTo(String assignedTo);
    long countByStatus(String status);
    long countByDatasetId(UUID datasetId);
    long countByDatasetIdAndStatusIn(UUID datasetId, List<String> statuses);
    long countByResolvedAtBetween(Instant from, Instant to);
    Optional<GovIssueTicket> findFirstBySourceTypeAndComplianceBatch_IdOrderByCreatedDateDesc(
        String sourceType,
        UUID complianceBatchId
    );

    Optional<GovIssueTicket> findFirstBySourceTypeIgnoreCaseAndSourceRefIdAndStatusInOrderByCreatedDateDesc(
        String sourceType,
        UUID sourceRefId,
        List<String> statuses
    );

    Optional<GovIssueTicket> findFirstBySourceTypeIgnoreCaseAndSourceRefIdOrderByCreatedDateDesc(
        String sourceType,
        UUID sourceRefId
    );

    long countByDatasetIdNotIn(java.util.Collection<UUID> datasetIds);

    List<GovIssueTicket> findByDatasetIdNotIn(java.util.Collection<UUID> datasetIds, org.springframework.data.domain.Pageable pageable);
}
