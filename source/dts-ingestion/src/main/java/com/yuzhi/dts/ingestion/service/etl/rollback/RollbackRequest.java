package com.yuzhi.dts.ingestion.service.etl.rollback;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RollbackRequest(
	@Min(value = 1, message = "level must be between 1 and 3")
	@Max(value = 3, message = "level must be between 1 and 3")
	int level,
	@NotBlank(message = "scope is required")
	@Pattern(regexp = "task|datasource", message = "scope must be task or datasource")
	String scope,
	@Positive(message = "taskId must be positive") Long taskId,
	UUID dataSourceId,
	@Size(max = 500, message = "tables must contain at most 500 entries")
	List<@NotBlank(message = "table name must not be blank") @Size(max = 512, message = "table name is too long") String> tables,
	boolean dryRun,
	UUID rollbackId,
	@Size(max = 128, message = "idempotencyKey is too long") String idempotencyKey,
	@Pattern(regexp = "[0-9a-fA-F]{64}", message = "requestHash must be a SHA-256 digest") String requestHash,
	@Valid AvailabilityFence availabilityFence
) {
	public RollbackRequest {
		tables = tables == null ? List.of() : List.copyOf(tables);
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, Object ignored) {
		throw new IllegalArgumentException("ROLLBACK_UNKNOWN_FIELD: " + field);
	}

	@JsonIgnore
	@AssertTrue(message = "scope requires exactly one matching target identifier")
	public boolean isScopeTargetValid() {
		return switch (scope == null ? "" : scope) {
			case "task" -> taskId != null && taskId > 0 && dataSourceId == null;
			case "datasource" -> taskId == null && dataSourceId != null;
			default -> false;
		};
	}

	@JsonIgnore
	@AssertTrue(message = "execute requires a committed platform availability fence")
	public boolean isExecutionFenceValid() {
		if (dryRun) {
			return rollbackId == null && idempotencyKey == null && requestHash == null && availabilityFence == null;
		}
		return rollbackId != null &&
			("platform:" + rollbackId).equals(idempotencyKey) &&
			requestHash != null &&
			requestHash.matches("[0-9a-fA-F]{64}") &&
			availabilityFence != null &&
			rollbackId.equals(availabilityFence.receiptId()) &&
			"PREPARED".equals(availabilityFence.state()) &&
			availabilityFence.sourceDataSourceId() != null &&
			availabilityFence.sourceSequence() > 0 &&
			availabilityFence.targetCount() > 0 &&
			(!"datasource".equals(scope) || availabilityFence.sourceDataSourceId().equals(dataSourceId));
	}

	@JsonIgnore
	@AssertTrue(message = "tables can only be specified for level 1 rollback")
	public boolean isTableSelectionValid() {
		return level == 1 || tables.isEmpty();
	}

	public record AvailabilityFence(
		@NotNull(message = "availabilityFence.receiptId is required") UUID receiptId,
		@NotNull(message = "availabilityFence.sourceDataSourceId is required") UUID sourceDataSourceId,
		@Positive(message = "availabilityFence.sourceSequence must be positive") long sourceSequence,
		@NotBlank(message = "availabilityFence.state is required")
		@Pattern(regexp = "PREPARED", message = "availabilityFence.state must be PREPARED")
		String state,
		@Positive(message = "availabilityFence.targetCount must be positive") int targetCount
	) {}
}
