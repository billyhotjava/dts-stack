package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.QueuedBuildGroup;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CommandResult;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.TransitionCommand;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Narrow transaction owner for START_BUILD.
 *
 * <p>The existing candidate command remains the only lifecycle state machine. This service adds
 * the invariant that its BUILDING transition, immutable build snapshot, active claims and one
 * QUEUED pipeline row per entry either all commit or all roll back.
 */
@Service
public class ModelMaterializationStartService {

    private final ModelReleaseCandidateService candidateCommands;
    private final ModelMaterializationBuildRepository builds;
    private final Clock clock;

    @Autowired
    public ModelMaterializationStartService(
        ModelReleaseCandidateService candidateCommands,
        ModelMaterializationBuildRepository builds
    ) {
        this(candidateCommands, builds, Clock.systemUTC());
    }

    ModelMaterializationStartService(
        ModelReleaseCandidateService candidateCommands,
        ModelMaterializationBuildRepository builds,
        Clock clock
    ) {
        this.candidateCommands = Objects.requireNonNull(
            candidateCommands,
            "candidateCommands is required"
        );
        this.builds = Objects.requireNonNull(builds, "builds is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Transactional
    public CommandResult start(
        String tenantId,
        String actorId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        return startWithBuild(
            tenantId,
            actorId,
            candidateId,
            expectedVersion,
            idempotencyKey,
            reason
        )
            .command();
    }

    @Transactional
    public StartResult startWithBuild(
        String tenantId,
        String actorId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        CommandResult result = candidateCommands.transition(
            tenantId,
            actorId,
            candidateId,
            new TransitionCommand(
                expectedVersion,
                DeliveryStatus.BUILDING,
                idempotencyKey,
                reason
            )
        );
        if (result.candidate().status() != DeliveryStatus.BUILDING) {
            // Canonical drift handling may atomically turn the requested build into STALE.
            return new StartResult(result, null);
        }
        QueuedBuildGroup build;
        if (result.replayed()) {
            build = builds.requireQueuedBuild(result.candidate());
        } else {
            build = builds.createQueuedBuild(result.candidate(), clock.instant());
        }
        return new StartResult(result, build);
    }

    @Transactional
    public CommandResult retry(
        String tenantId,
        String actorId,
        UUID candidateId,
        int expectedVersion,
        String idempotencyKey,
        String reason
    ) {
        CommandResult result = candidateCommands.transition(
            tenantId,
            actorId,
            candidateId,
            new TransitionCommand(
                expectedVersion,
                DeliveryStatus.BUILDING,
                idempotencyKey,
                reason
            )
        );
        if (result.candidate().status() != DeliveryStatus.BUILDING) {
            return result;
        }
        if (result.replayed()) {
            builds.requireQueuedBuild(result.candidate());
        } else {
            builds.createRetryQueuedBuild(result.candidate(), clock.instant());
        }
        return result;
    }

    public record StartResult(CommandResult command, QueuedBuildGroup build) {
        public StartResult {
            Objects.requireNonNull(command, "command is required");
            if (
                command.candidate().status() == DeliveryStatus.BUILDING &&
                build == null
            ) {
                throw new IllegalArgumentException(
                    "BUILDING candidate requires a durable build group"
                );
            }
        }
    }
}
