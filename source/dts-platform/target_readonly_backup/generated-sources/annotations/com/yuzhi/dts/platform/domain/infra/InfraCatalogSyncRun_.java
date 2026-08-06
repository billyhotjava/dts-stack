package com.yuzhi.dts.platform.domain.infra;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(InfraCatalogSyncRun.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class InfraCatalogSyncRun_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String REASON = "reason";
	public static final String CATALOG_DATASET_COUNT_AFTER = "catalogDatasetCountAfter";
	public static final String TABLES_CREATED = "tablesCreated";
	public static final String STARTED_AT = "startedAt";
	public static final String DATASETS_UPDATED = "datasetsUpdated";
	public static final String ERROR = "error";
	public static final String CATALOG_DATASET_COUNT_BEFORE = "catalogDatasetCountBefore";
	public static final String FINISHED_AT = "finishedAt";
	public static final String COLUMNS_IMPORTED = "columnsImported";
	public static final String TABLES_DISCOVERED = "tablesDiscovered";
	public static final String INTEGRATION = "integration";
	public static final String ID = "id";
	public static final String DATASETS_REMOVED = "datasetsRemoved";
	public static final String DATASETS_CREATED = "datasetsCreated";
	public static final String STATUS = "status";
	public static final String DETAILS_JSON = "detailsJson";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#reason
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, String> reason;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#catalogDatasetCountAfter
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, Long> catalogDatasetCountAfter;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#tablesCreated
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, Integer> tablesCreated;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#startedAt
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, Instant> startedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#datasetsUpdated
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, Integer> datasetsUpdated;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#error
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, String> error;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#catalogDatasetCountBefore
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, Long> catalogDatasetCountBefore;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#finishedAt
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, Instant> finishedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#columnsImported
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, Integer> columnsImported;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#tablesDiscovered
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, Integer> tablesDiscovered;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#integration
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, String> integration;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#id
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#datasetsRemoved
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, Integer> datasetsRemoved;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun
	 **/
	public static volatile EntityType<InfraCatalogSyncRun> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#datasetsCreated
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, Integer> datasetsCreated;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#status
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, String> status;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncRun#detailsJson
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncRun, String> detailsJson;

}

