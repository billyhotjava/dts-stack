package com.yuzhi.dts.ingestion.service.etl.rollback;

/**
 * Pluggable confirmation policy for rollback operations.
 * Current: MODAL (simple confirm dialog).
 * Future: INPUT_NAME (type entity name to confirm), ADMIN_APPROVE (require admin role).
 */
public interface ConfirmationPolicy {

	boolean requiresConfirmation(RollbackLevel level);

	String confirmationType(RollbackLevel level);
}
