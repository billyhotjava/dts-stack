package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovIssueAction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovIssueActionRepository extends JpaRepository<GovIssueAction, UUID> {
    List<GovIssueAction> findByTicketIdOrderByCreatedDateAsc(UUID ticketId);
}

