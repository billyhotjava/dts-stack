package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogClassificationPropagationJob.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogClassificationPropagationJob_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String LAST_ERROR = "lastError";
	public static final String TARGET_DATASET_ID = "targetDatasetId";
	public static final String IDEMPOTENCY_KEY = "idempotencyKey";
	public static final String NEXT_ATTEMPT_AT = "nextAttemptAt";
	public static final String ID = "id";
	public static final String TARGET_ASSET_KEY = "targetAssetKey";
	public static final String TRIGGER_TYPE = "triggerType";
	public static final String TRIGGER_REF = "triggerRef";
	public static final String STATUS = "status";
	public static final String ATTEMPTS = "attempts";
	public static final String AFFECTED_SUBJECTS = "affectedSubjects";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob#lastError
	 **/
	public static volatile SingularAttribute<CatalogClassificationPropagationJob, String> lastError;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob#targetDatasetId
	 **/
	public static volatile SingularAttribute<CatalogClassificationPropagationJob, UUID> targetDatasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob#idempotencyKey
	 **/
	public static volatile SingularAttribute<CatalogClassificationPropagationJob, String> idempotencyKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob#nextAttemptAt
	 **/
	public static volatile SingularAttribute<CatalogClassificationPropagationJob, Instant> nextAttemptAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob#id
	 **/
	public static volatile SingularAttribute<CatalogClassificationPropagationJob, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob#targetAssetKey
	 **/
	public static volatile SingularAttribute<CatalogClassificationPropagationJob, String> targetAssetKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob#triggerType
	 **/
	public static volatile SingularAttribute<CatalogClassificationPropagationJob, String> triggerType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob
	 **/
	public static volatile EntityType<CatalogClassificationPropagationJob> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob#triggerRef
	 **/
	public static volatile SingularAttribute<CatalogClassificationPropagationJob, String> triggerRef;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob#status
	 **/
	public static volatile SingularAttribute<CatalogClassificationPropagationJob, String> status;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob#attempts
	 **/
	public static volatile SingularAttribute<CatalogClassificationPropagationJob, Integer> attempts;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationPropagationJob#affectedSubjects
	 **/
	public static volatile SingularAttribute<CatalogClassificationPropagationJob, Integer> affectedSubjects;

}

