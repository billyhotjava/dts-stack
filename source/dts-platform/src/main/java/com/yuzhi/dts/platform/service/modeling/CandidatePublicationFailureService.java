package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists only the failure convergence after the atomic publication transaction has rolled back. */
@Service
public class CandidatePublicationFailureService {

    private final ModelReleaseCandidateService candidateCommands;

    public CandidatePublicationFailureService(ModelReleaseCandidateService candidateCommands) {
        this.candidateCommands = candidateCommands;
    }

    @Transactional
    public CommandResult markPartial(
        String tenantId,
        String actorId,
        CandidateView publishing,
        RuntimeException failure
    ) {
        String message = failure == null || failure.getMessage() == null
            ? "Candidate publication commit failed"
            : failure.getClass().getSimpleName() + ": " + failure.getMessage();
        if (message.length() > 1_024) message = message.substring(0, 1_024);
        return candidateCommands.transition(
            tenantId,
            actorId,
            publishing.id(),
            new TransitionCommand(
                publishing.version(),
                DeliveryStatus.PARTIAL,
                "candidate-publication-failed:" + publishing.id() + ":v" + publishing.version(),
                message
            )
        );
    }
}
