package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovIssueTicket;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovIssueTicketRepository extends JpaRepository<GovIssueTicket, UUID> {
    List<GovIssueTicket> findByStatusInOrderByCreatedDateDesc(List<String> statuses);
    List<GovIssueTicket> findByAssignedTo(String assignedTo);
    long countByStatus(String status);
    long countByResolvedAtBetween(Instant from, Instant to);
    Optional<GovIssueTicket> findFirstBySourceTypeAndSourceIdOrderByCreatedDateDesc(String sourceType, UUID sourceId);
}

