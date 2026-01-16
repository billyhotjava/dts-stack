package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminWorkflowTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminWorkflowTemplateRepository extends JpaRepository<AdminWorkflowTemplate, UUID> {
    List<AdminWorkflowTemplate> findByWorkflowTypeIgnoreCaseOrderByPriorityDescCreatedDateDesc(String workflowType);
    List<AdminWorkflowTemplate> findByWorkflowTypeIgnoreCaseAndEnabledTrueOrderByPriorityDescCreatedDateDesc(String workflowType);
}

