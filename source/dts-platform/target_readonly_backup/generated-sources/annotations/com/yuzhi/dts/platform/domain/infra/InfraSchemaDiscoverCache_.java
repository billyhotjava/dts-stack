package com.yuzhi.dts.platform.domain.infra;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(InfraSchemaDiscoverCache.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class InfraSchemaDiscoverCache_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String TABLE_COUNT = "tableCount";
	public static final String DRIFT_ADDED_TABLES = "driftAddedTables";
	public static final String DRIFT_CHANGED_TABLES = "driftChangedTables";
	public static final String DRIFT_REMOVED_TABLES = "driftRemovedTables";
	public static final String TABLE_PATTERN = "tablePattern";
	public static final String COLUMN_COUNT = "columnCount";
	public static final String SCHEMA_NAME = "schemaName";
	public static final String RESPONSE_JSON = "responseJson";
	public static final String ENABLED = "enabled";
	public static final String DATA_SOURCE_ID = "dataSourceId";
	public static final String CACHE_KEY = "cacheKey";
	public static final String REFRESHED_AT = "refreshedAt";
	public static final String ID = "id";
	public static final String DRIFT_DETAILS_JSON = "driftDetailsJson";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#tableCount
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, Integer> tableCount;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#driftAddedTables
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, Integer> driftAddedTables;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#driftChangedTables
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, Integer> driftChangedTables;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#driftRemovedTables
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, Integer> driftRemovedTables;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#tablePattern
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, String> tablePattern;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#columnCount
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, Integer> columnCount;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#schemaName
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, String> schemaName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#responseJson
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, String> responseJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#enabled
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, Boolean> enabled;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#dataSourceId
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, UUID> dataSourceId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#cacheKey
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, String> cacheKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#refreshedAt
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, Instant> refreshedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#id
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#driftDetailsJson
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, String> driftDetailsJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache
	 **/
	public static volatile EntityType<InfraSchemaDiscoverCache> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraSchemaDiscoverCache#status
	 **/
	public static volatile SingularAttribute<InfraSchemaDiscoverCache, String> status;

}

