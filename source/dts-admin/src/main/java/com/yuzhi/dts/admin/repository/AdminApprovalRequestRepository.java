package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminApprovalRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminApprovalRequestRepository extends JpaRepository<AdminApprovalRequest, Long> {

    Page<AdminApprovalRequest> findAllByStatusIn(List<String> statuses, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Optional<AdminApprovalRequest> findWithItemsById(Long id);
}
