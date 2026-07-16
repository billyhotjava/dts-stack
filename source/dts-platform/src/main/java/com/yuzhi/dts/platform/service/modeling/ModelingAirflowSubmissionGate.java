package com.yuzhi.dts.platform.service.modeling;

import java.util.List;
import java.util.Set;

/** Decides whether a compiled ModelSpec may be handed to Airflow. */
public final class ModelingAirflowSubmissionGate {

    private ModelingAirflowSubmissionGate() {}

    public enum CompileStatus {
        COMPILED,
        FAILED,
    }

    public record Decision(boolean submittable, List<String> blockers, String dbtSelector, String targetTable) {}

    public static Decision evaluate(
        CompileStatus compileStatus,
        ModelingRunRequestContract.RunRequest request,
        Set<String> knownSourceBatches
    ) {
        if (compileStatus != CompileStatus.COMPILED) return new Decision(false, List.of("COMPILE_REQUIRED"), null, null);
        List<ModelingRunRequestContract.Issue> issues = ModelingRunRequestContract.validate(request, knownSourceBatches);
        if (!issues.isEmpty()) return new Decision(false, issues.stream().map(issue -> issue.code().name()).toList(), null, null);
        ModelingRunRequestContract.ExternalContext context = request.externalContext();
        return new Decision(true, List.of(), context.dbtSelector(), context.targetTable());
    }
}
