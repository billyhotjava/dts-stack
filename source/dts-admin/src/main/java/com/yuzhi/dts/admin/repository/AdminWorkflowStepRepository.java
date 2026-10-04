package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminWorkflowStep;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminWorkflowStepRepository extends JpaRepository<AdminWorkflowStep, UUID> {
    List<AdminWorkflowStep> findByTemplateIdOrderByStepOrderAsc(UUID templateId);
    void deleteByTemplateId(UUID templateId);
}

