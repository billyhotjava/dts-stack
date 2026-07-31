package com.yuzhi.dts.ingestion.service.etl.rollback;

import java.util.ArrayList;
import java.util.List;

public record RollbackResult(
	boolean success,
	List<String> actions,           // what was done
	List<String> errors,            // what failed
	boolean dbtFullRefreshNeeded,   // signal to dts-platform
	List<Long> affectedTaskIds      // for dts-platform cascade
) {
	public String status() {
		if (errors == null || errors.isEmpty()) {
			return "SUCCESS";
		}
		return actions == null || actions.isEmpty() ? "FAILED" : "PARTIAL";
	}

	public static RollbackResult empty(String message) {
		return new RollbackResult(true, List.of(message), List.of(), false, List.of());
	}

	public static RollbackResult merge(List<RollbackResult> results) {
		List<String> allActions = new ArrayList<>();
		List<String> allErrors = new ArrayList<>();
		List<Long> allTaskIds = new ArrayList<>();
		boolean anyDbt = false;
		for (RollbackResult r : results) {
			allActions.addAll(r.actions());
			allErrors.addAll(r.errors());
			allTaskIds.addAll(r.affectedTaskIds());
			if (r.dbtFullRefreshNeeded()) anyDbt = true;
		}
		return new RollbackResult(allErrors.isEmpty(), allActions, allErrors, anyDbt, allTaskIds);
	}
}
