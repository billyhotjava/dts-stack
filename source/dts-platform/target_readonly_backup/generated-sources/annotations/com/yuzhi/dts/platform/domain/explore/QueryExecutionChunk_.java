package com.yuzhi.dts.platform.domain.explore;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(QueryExecutionChunk.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class QueryExecutionChunk_ {

	public static final String EXECUTION_ID = "executionId";
	public static final String ROWS_JSON = "rowsJson";
	public static final String CREATED_DATE = "createdDate";
	public static final String ROW_START = "rowStart";
	public static final String ROW_END = "rowEnd";
	public static final String ID = "id";
	public static final String CHUNK_INDEX = "chunkIndex";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecutionChunk#executionId
	 **/
	public static volatile SingularAttribute<QueryExecutionChunk, UUID> executionId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecutionChunk#rowsJson
	 **/
	public static volatile SingularAttribute<QueryExecutionChunk, String> rowsJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecutionChunk#createdDate
	 **/
	public static volatile SingularAttribute<QueryExecutionChunk, Instant> createdDate;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecutionChunk#rowStart
	 **/
	public static volatile SingularAttribute<QueryExecutionChunk, Long> rowStart;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecutionChunk#rowEnd
	 **/
	public static volatile SingularAttribute<QueryExecutionChunk, Long> rowEnd;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecutionChunk#id
	 **/
	public static volatile SingularAttribute<QueryExecutionChunk, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecutionChunk#chunkIndex
	 **/
	public static volatile SingularAttribute<QueryExecutionChunk, Integer> chunkIndex;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryExecutionChunk
	 **/
	public static volatile EntityType<QueryExecutionChunk> class_;

}

