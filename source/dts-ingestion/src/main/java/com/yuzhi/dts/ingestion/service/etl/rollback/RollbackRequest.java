package com.yuzhi.dts.ingestion.service.etl.rollback;

import java.util.List;
import java.util.UUID;

public record RollbackRequest(
	int level,              // 1, 2, 3
	String scope,           // "task" | "datasource"
	Long taskId,            // required when scope=task
	UUID dataSourceId,      // required when scope=datasource
	List<String> tables,    // Level 1: specific tables to truncate (null=all)
	boolean rebuildDbt,     // Level 2: trigger dbt run --full-refresh
	boolean dryRun          // true=analyze only, false=execute
) {}
