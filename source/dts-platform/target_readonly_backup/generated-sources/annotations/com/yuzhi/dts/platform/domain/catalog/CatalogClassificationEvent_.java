package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogClassificationEvent.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogClassificationEvent_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String PREVIOUS_LEVEL = "previousLevel";
	public static final String OCCURRED_AT = "occurredAt";
	public static final String EVENT_TYPE = "eventType";
	public static final String CANDIDATE_LEVEL = "candidateLevel";
	public static final String SUBJECT_TYPE = "subjectType";
	public static final String RESULTING_LEVEL = "resultingLevel";
	public static final String ASSET_TYPE = "assetType";
	public static final String ACTOR = "actor";
	public static final String ID = "id";
	public static final String TRIGGER_TYPE = "triggerType";
	public static final String SNAPSHOT_VERSION = "snapshotVersion";
	public static final String SUBJECT_KEY = "subjectKey";
	public static final String TRIGGER_REF = "triggerRef";
	public static final String EVIDENCE_JSON = "evidenceJson";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent#previousLevel
	 **/
	public static volatile SingularAttribute<CatalogClassificationEvent, String> previousLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent#occurredAt
	 **/
	public static volatile SingularAttribute<CatalogClassificationEvent, Instant> occurredAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent#eventType
	 **/
	public static volatile SingularAttribute<CatalogClassificationEvent, String> eventType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent#candidateLevel
	 **/
	public static volatile SingularAttribute<CatalogClassificationEvent, String> candidateLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent#subjectType
	 **/
	public static volatile SingularAttribute<CatalogClassificationEvent, String> subjectType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent#resultingLevel
	 **/
	public static volatile SingularAttribute<CatalogClassificationEvent, String> resultingLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent#assetType
	 **/
	public static volatile SingularAttribute<CatalogClassificationEvent, String> assetType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent#actor
	 **/
	public static volatile SingularAttribute<CatalogClassificationEvent, String> actor;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent#id
	 **/
	public static volatile SingularAttribute<CatalogClassificationEvent, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent#triggerType
	 **/
	public static volatile SingularAttribute<CatalogClassificationEvent, String> triggerType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent#snapshotVersion
	 **/
	public static volatile SingularAttribute<CatalogClassificationEvent, Long> snapshotVersion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent
	 **/
	public static volatile EntityType<CatalogClassificationEvent> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent#subjectKey
	 **/
	public static volatile SingularAttribute<CatalogClassificationEvent, String> subjectKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent#triggerRef
	 **/
	public static volatile SingularAttribute<CatalogClassificationEvent, String> triggerRef;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationEvent#evidenceJson
	 **/
	public static volatile SingularAttribute<CatalogClassificationEvent, String> evidenceJson;

}

