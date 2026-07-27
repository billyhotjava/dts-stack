package com.yuzhi.dts.platform.service.etl;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One external execution port shared by canonical model materialization entry points.
 *
 * <p>The request intentionally contains no project path, selector, target, image, command or
 * credential. Airflow obtains those server-controlled values by consuming the runtime spec token.
 */
public interface DbtExecutionGateway {

    SubmissionResult submitReleaseBuild(ReleaseBuildRequest request);

    /**
     * Reads one deterministic DagRun without creating an external side effect.
     *
     * <p>An empty result means Airflow authoritatively returned "not found". A present UNKNOWN
     * result means absence could not be proven and callers must not submit a second request.
     */
    Optional<SubmissionResult> reconcileReleaseBuild(
        ReleaseBuildRequest request
    );

    enum SubmissionStatus {
        SUBMITTED,
        BLOCKED,
        RETRYABLE_UNKNOWN,
    }

    record ReleaseBuildRequest(
        UUID pipelineRunGroupId,
        UUID candidateId,
        int candidateVersion,
        int attempt,
        String dagId,
        String dagRunId,
        String runtimeSpecToken,
        String bundleChecksum
    ) {
        private static final Pattern SAFE_DAG_ID = Pattern.compile(
            "^[a-z][a-z0-9_]{2,199}$"
        );
        private static final Pattern SAFE_DAG_RUN_ID = Pattern.compile(
            "^[A-Za-z0-9_.:+~-]{3,250}$"
        );
        private static final Pattern SHA256 = Pattern.compile(
            "^[0-9a-f]{64}$"
        );

        public ReleaseBuildRequest {
            Objects.requireNonNull(
                pipelineRunGroupId,
                "pipelineRunGroupId is required"
            );
            Objects.requireNonNull(candidateId, "candidateId is required");
            if (candidateVersion < 1 || attempt < 1) {
                throw new IllegalArgumentException(
                    "candidateVersion and attempt must be positive"
                );
            }
            dagId = required(dagId, "dagId");
            dagRunId = required(dagRunId, "dagRunId");
            runtimeSpecToken = required(
                runtimeSpecToken,
                "runtimeSpecToken"
            );
            bundleChecksum = required(
                bundleChecksum,
                "bundleChecksum"
            );
            if (!SAFE_DAG_ID.matcher(dagId).matches()) {
                throw new IllegalArgumentException("dagId is invalid");
            }
            if (!SAFE_DAG_RUN_ID.matcher(dagRunId).matches()) {
                throw new IllegalArgumentException("dagRunId is invalid");
            }
            if (!SHA256.matcher(bundleChecksum).matches()) {
                throw new IllegalArgumentException(
                    "bundleChecksum is invalid"
                );
            }
        }

        private static String required(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " is required");
            }
            return value.trim();
        }
    }

    record SubmissionResult(
        SubmissionStatus status,
        String dagRunId,
        boolean recovered,
        String errorCode
    ) {
        public SubmissionResult {
            Objects.requireNonNull(status, "status is required");
            if (
                status == SubmissionStatus.SUBMITTED &&
                (dagRunId == null || dagRunId.isBlank())
            ) {
                throw new IllegalArgumentException(
                    "submitted result requires dagRunId"
                );
            }
            if (
                status != SubmissionStatus.SUBMITTED &&
                (errorCode == null || errorCode.isBlank())
            ) {
                throw new IllegalArgumentException(
                    "non-submitted result requires errorCode"
                );
            }
        }

        public static SubmissionResult submitted(
            String dagRunId,
            boolean recovered
        ) {
            return new SubmissionResult(
                SubmissionStatus.SUBMITTED,
                dagRunId,
                recovered,
                null
            );
        }

        public static SubmissionResult blocked(String code) {
            return new SubmissionResult(
                SubmissionStatus.BLOCKED,
                null,
                false,
                code
            );
        }

        public static SubmissionResult retryableUnknown(
            String dagRunId,
            String code
        ) {
            return new SubmissionResult(
                SubmissionStatus.RETRYABLE_UNKNOWN,
                dagRunId,
                false,
                code
            );
        }
    }
}
