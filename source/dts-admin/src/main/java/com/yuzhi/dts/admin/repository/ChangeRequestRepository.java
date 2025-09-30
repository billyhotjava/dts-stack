package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.ChangeRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, Long> {
    List<ChangeRequest> findByStatus(String status);
    List<ChangeRequest> findByRequestedBy(String requestedBy);
    List<ChangeRequest> findByStatusAndResourceType(String status, String resourceType);
}

