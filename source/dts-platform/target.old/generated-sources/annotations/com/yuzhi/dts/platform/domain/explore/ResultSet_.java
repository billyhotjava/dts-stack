package com.yuzhi.dts.platform.domain.explore;

import com.yuzhi.dts.platform.domain.explore.ResultSet.StorageFormat;
import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(ResultSet.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class ResultSet_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String TTL_DAYS = "ttlDays";
	public static final String PREVIEW_COLUMNS = "previewColumns";
	public static final String COLUMNS = "columns";
	public static final String CHUNK_COUNT = "chunkCount";
	public static final String STORAGE_URI = "storageUri";
	public static final String STORAGE_FORMAT = "storageFormat";
	public static final String ID = "id";
	public static final String ROW_COUNT = "rowCount";
	public static final String EXPIRES_AT = "expiresAt";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ResultSet#ttlDays
	 **/
	public static volatile SingularAttribute<ResultSet, Integer> ttlDays;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ResultSet#previewColumns
	 **/
	public static volatile SingularAttribute<ResultSet, String> previewColumns;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ResultSet#columns
	 **/
	public static volatile SingularAttribute<ResultSet, String> columns;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ResultSet#chunkCount
	 **/
	public static volatile SingularAttribute<ResultSet, Integer> chunkCount;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ResultSet#storageUri
	 **/
	public static volatile SingularAttribute<ResultSet, String> storageUri;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ResultSet#storageFormat
	 **/
	public static volatile SingularAttribute<ResultSet, StorageFormat> storageFormat;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ResultSet#id
	 **/
	public static volatile SingularAttribute<ResultSet, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ResultSet#rowCount
	 **/
	public static volatile SingularAttribute<ResultSet, Long> rowCount;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ResultSet
	 **/
	public static volatile EntityType<ResultSet> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ResultSet#expiresAt
	 **/
	public static volatile SingularAttribute<ResultSet, Instant> expiresAt;

}

