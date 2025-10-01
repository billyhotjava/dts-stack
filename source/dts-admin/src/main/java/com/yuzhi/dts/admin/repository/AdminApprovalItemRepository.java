package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminApprovalItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminApprovalItemRepository extends JpaRepository<AdminApprovalItem, Long> {

    List<AdminApprovalItem> findByRequestIdOrderBySeqNumberAsc(Long requestId);
}
