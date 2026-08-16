package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Keeps the rollback boundary explicit: commit is one transaction; PARTIAL convergence starts only after rollback.
 */
@Service
public class CandidatePublicationCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(CandidatePublicationCoordinator.class);

    private final CandidatePublicationCommitService commits;
    private final CandidatePublicationFailureService failures;

    public CandidatePublicationCoordinator(
        CandidatePublicationCommitService commits,
        CandidatePublicationFailureService failures
    ) {
        this.commits = commits;
        this.failures = failures;
    }

    public CommandResult publish(
        String tenantId,
        String actorId,
        CandidateView publishing,
        String publishRequestKey,
        String reason
    ) {
        try {
            return commits.commit(tenantId, actorId, publishing, publishRequestKey, reason);
        } catch (RuntimeException failure) {
            LOG.error(
                "Candidate publication commit failed: candidateId={}, status={}, actorId={}, failureType={}, message={}",
                publishing == null ? null : publishing.id(),
                publishing == null ? null : publishing.status(),
                actorId,
                failure.getClass().getSimpleName(),
                failure.getMessage(),
                failure
            );
            if (
                publishing != null &&
                publishing.status() == ModelLifecycleContract.DeliveryStatus.PARTIAL
            ) {
                throw failure;
            }
            return failures.markPartial(tenantId, actorId, publishing, failure);
        }
    }
}
