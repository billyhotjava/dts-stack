package com.yuzhi.dts.platform.domain.explore;

import com.yuzhi.dts.platform.domain.explore.ExecEnums.ExecEngine;
import com.yuzhi.dts.platform.domain.explore.ExecEnums.ExecStatus;
import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(QueryExecution.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class QueryExecution_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SQL_TEXT = "sqlText";
	public static final String QUEUE_POSITION = "queuePosition";
	public static final String WARNINGS = "warnings";
	public static final String ERROR_MESSAGE = "errorMessage";
	public static final String STARTED_AT = "startedAt";
	public static final String LIMIT_APPLIED = "limitApplied";
	public static final String PLAN_DIGEST = "planDigest";
	public static final String RESULT_SET_ID = "resultSetId";
	public static final String FINISHED_AT = "finishedAt";
	public static final String QUERY_HASH = "queryHash";
	public static final String TRINO_QUERY_ID = "trinoQueryId";
	public static final String SAVED_QUERY_ID = "savedQueryId";
	public static final String ENGINE = "engine";
	public static final String DATASOURCE = "datasource";
	public static final String DATASET_ID = "datasetId";
	public static final String CONNECTION = "connection";
	public static final String ID = "id";
	public static final String ROW_COUNT = "rowCount";
	public static final String BYTES_PROCESSED = "bytesProcessed";
	public static final String STATUS = "status";
	public static final String ELAPSED_MS = "elapsedMs";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#sqlText
	 **/
	public static volatile SingularAttribute<QueryExecution, String> sqlText;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#queuePosition
	 **/
	public static volatile SingularAttribute<QueryExecution, Integer> queuePosition;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#warnings
	 **/
	public static volatile SingularAttribute<QueryExecution, String> warnings;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#errorMessage
	 **/
	public static volatile SingularAttribute<QueryExecution, String> errorMessage;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#startedAt
	 **/
	public static volatile SingularAttribute<QueryExecution, Instant> startedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#limitApplied
	 **/
	public static volatile SingularAttribute<QueryExecution, Boolean> limitApplied;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#planDigest
	 **/
	public static volatile SingularAttribute<QueryExecution, String> planDigest;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#resultSetId
	 **/
	public static volatile SingularAttribute<QueryExecution, UUID> resultSetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#finishedAt
	 **/
	public static volatile SingularAttribute<QueryExecution, Instant> finishedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#queryHash
	 **/
	public static volatile SingularAttribute<QueryExecution, String> queryHash;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#trinoQueryId
	 **/
	public static volatile SingularAttribute<QueryExecution, String> trinoQueryId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#savedQueryId
	 **/
	public static volatile SingularAttribute<QueryExecution, UUID> savedQueryId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#engine
	 **/
	public static volatile SingularAttribute<QueryExecution, ExecEngine> engine;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#datasource
	 **/
	public static volatile SingularAttribute<QueryExecution, String> datasource;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#datasetId
	 **/
	public static volatile SingularAttribute<QueryExecution, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#connection
	 **/
	public static volatile SingularAttribute<QueryExecution, String> connection;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#id
	 **/
	public static volatile SingularAttribute<QueryExecution, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#rowCount
	 **/
	public static volatile SingularAttribute<QueryExecution, Long> rowCount;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution
	 **/
	public static volatile EntityType<QueryExecution> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#bytesProcessed
	 **/
	public static volatile SingularAttribute<QueryExecution, Long> bytesProcessed;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#status
	 **/
	public static volatile SingularAttribute<QueryExecution, ExecStatus> status;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecution#elapsedMs
	 **/
	public static volatile SingularAttribute<QueryExecution, Long> elapsedMs;

}

