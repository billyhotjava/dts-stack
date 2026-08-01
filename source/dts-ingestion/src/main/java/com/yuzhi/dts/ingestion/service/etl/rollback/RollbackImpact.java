package com.yuzhi.dts.ingestion.service.etl.rollback;

import java.util.List;
import java.util.UUID;

public record RollbackImpact(
	int level,
	String scope,
	Long taskId,
	UUID dataSourceId,
	List<String> affectedTables,
	List<String> uploadFiles,
	int executionRecords,
	String confirmationType,
	List<Long> cascadeTaskIds,
	List<String> warnings               // hints for Excel/CSV scenarios
) {}
