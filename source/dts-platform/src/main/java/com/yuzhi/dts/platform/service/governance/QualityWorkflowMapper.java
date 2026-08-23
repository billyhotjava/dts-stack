package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityWorkflowRun;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityWorkflowRunDto;
import java.util.List;

final class QualityWorkflowMapper {

    private QualityWorkflowMapper() {}

    static QualityWorkflowRunDto toDto(GovQualityWorkflowRun workflow, List<QualityRunDto> ruleRuns) {
        if (workflow == null) {
            return null;
        }
        return new QualityWorkflowRunDto(
            workflow.getId(),
            workflow.getTaskId(),
            workflow.getDatasetId(),
            workflow.getRuleId(),
            workflow.getRetryOfId(),
            workflow.getAttemptNo(),
            workflow.getMaxRetryAttempts(),
            workflow.getRetryBackoffSeconds(),
            workflow.getTriggerType(),
            workflow.getTriggerRef(),
            workflow.getStatus(),
            workflow.getExpectedRunCount(),
            workflow.getCompletedRunCount(),
            workflow.getPassedCount(),
            workflow.getFailedCount(),
            workflow.getDispatchFailureCount(),
            workflow.getScheduledAt(),
            workflow.getStartedAt(),
            workflow.getFinishedAt(),
            workflow.getErrorCategory(),
            workflow.getMessage(),
            workflow.getContextJson(),
            workflow.getCreatedDate(),
            workflow.getCreatedBy(),
            ruleRuns == null ? List.of() : List.copyOf(ruleRuns)
        );
    }
}
