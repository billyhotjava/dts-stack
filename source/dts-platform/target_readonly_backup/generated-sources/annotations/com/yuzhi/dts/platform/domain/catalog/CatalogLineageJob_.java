package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogLineageJob.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogLineageJob_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String RELATION_TYPE = "relationType";
	public static final String SOURCE_ID = "sourceId";
	public static final String DETAIL_PAYLOAD = "detailPayload";
	public static final String LAST_OBSERVED_AT = "lastObservedAt";
	public static final String JOB_KEY = "jobKey";
	public static final String EXTERNAL_ID = "externalId";
	public static final String ENGINE = "engine";
	public static final String NAME = "name";
	public static final String LAST_EXECUTION_STATUS = "lastExecutionStatus";
	public static final String ID = "id";
	public static final String LAST_VERIFIED_AT = "lastVerifiedAt";
	public static final String JOB_TYPE = "jobType";
	public static final String PROJECT_NAME = "projectName";
	public static final String LAST_EXECUTION_ID = "lastExecutionId";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#relationType
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, String> relationType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#sourceId
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, UUID> sourceId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#detailPayload
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, String> detailPayload;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#lastObservedAt
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, Instant> lastObservedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#jobKey
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, String> jobKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#externalId
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, String> externalId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#engine
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, String> engine;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#name
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#lastExecutionStatus
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, String> lastExecutionStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#id
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#lastVerifiedAt
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, Instant> lastVerifiedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#jobType
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, String> jobType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#projectName
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, String> projectName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#lastExecutionId
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, String> lastExecutionId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob
	 **/
	public static volatile EntityType<CatalogLineageJob> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#ownerDept
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob#status
	 **/
	public static volatile SingularAttribute<CatalogLineageJob, String> status;

}

