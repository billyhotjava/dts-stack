package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.CompletionCommand;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.CompletionOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RollbackInvalidationCompletionRequest(
    @NotNull UUID receiptId,
    @NotBlank @Size(max = 128) String eventId,
    @Positive long sourceSequence,
    @NotNull CompletionOutcome outcome,
    @Size(max = 512) String reason,
    boolean zeroSideEffectsConfirmed,
    @Size(max = 256) String downstreamReference
) {
    public CompletionCommand toCommand() {
        return new CompletionCommand(
            receiptId,
            eventId,
            sourceSequence,
            outcome,
            reason,
            zeroSideEffectsConfirmed,
            downstreamReference
        );
    }
}
