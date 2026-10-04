package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminApprovalItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminApprovalItemRepository extends JpaRepository<AdminApprovalItem, Long> {

    List<AdminApprovalItem> findByRequestIdOrderBySeqNumberAsc(Long requestId);

    @Query(
        value = """
            select exists(
                select 1
                from admin_approval_item item
                where item.payload_json ->> 'changeRequestId' = :changeRequestId
            )
        """,
        nativeQuery = true
    )
    boolean existsByChangeRequestId(@Param("changeRequestId") String changeRequestId);
}
