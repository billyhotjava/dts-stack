package com.yuzhi.dts.ingestion.service.etl.rollback;

import java.util.List;
import java.util.UUID;

public record RollbackResult(
	boolean success,
	List<String> actions,
	List<String> errors,
	List<Long> affectedTaskIds,
	boolean sideEffectsApplied,
	String sideEffectStatus,
	UUID receiptId,
	String outcome,
	String completionEventId,
	boolean completionPending,
	boolean replayed
) {
	public RollbackResult {
		actions = actions == null ? List.of() : List.copyOf(actions);
		errors = errors == null ? List.of() : List.copyOf(errors);
		affectedTaskIds = affectedTaskIds == null ? List.of() : List.copyOf(affectedTaskIds);
	}

	public RollbackResult(boolean success, List<String> actions, List<String> errors, List<Long> affectedTaskIds) {
		this(
			success,
			actions,
			errors,
			affectedTaskIds,
			actions != null && !actions.isEmpty(),
			actions == null || actions.isEmpty() ? "NONE" : "APPLIED",
			null,
			null,
			null,
			false,
			false
		);
	}

	public String status() {
		if (errors.isEmpty()) {
			return "SUCCESS";
		}
		return sideEffectsApplied ? "PARTIAL" : "FAILED";
	}

	public RollbackResult withCompletion(
		UUID newReceiptId,
		String newOutcome,
		String eventId,
		boolean pending,
		boolean wasReplayed
	) {
		return new RollbackResult(
			success,
			actions,
			errors,
			affectedTaskIds,
			sideEffectsApplied,
			sideEffectStatus,
			newReceiptId,
			newOutcome,
			eventId,
			pending,
			wasReplayed
		);
	}
}
