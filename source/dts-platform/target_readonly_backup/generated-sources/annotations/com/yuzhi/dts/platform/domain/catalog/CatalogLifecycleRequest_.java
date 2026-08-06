package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogLifecycleRequest.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogLifecycleRequest_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String NOTES = "notes";
	public static final String REQUEST_TYPE = "requestType";
	public static final String PREVIOUS_EXPIRES_AT = "previousExpiresAt";
	public static final String DECIDED_BY = "decidedBy";
	public static final String PREVIOUS_LIFECYCLE_STATUS = "previousLifecycleStatus";
	public static final String DECISION_NOTES = "decisionNotes";
	public static final String REQUESTED_LIFECYCLE_STATUS = "requestedLifecycleStatus";
	public static final String DECIDED_AT = "decidedAt";
	public static final String DATASET_ID = "datasetId";
	public static final String PREVIOUS_RETENTION_DAYS = "previousRetentionDays";
	public static final String REQUESTED_RETENTION_DAYS = "requestedRetentionDays";
	public static final String ID = "id";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String REQUESTED_EXPIRES_AT = "requestedExpiresAt";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#notes
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, String> notes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#requestType
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, String> requestType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#previousExpiresAt
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, Instant> previousExpiresAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#decidedBy
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, String> decidedBy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#previousLifecycleStatus
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, String> previousLifecycleStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#decisionNotes
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, String> decisionNotes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#requestedLifecycleStatus
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, String> requestedLifecycleStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#decidedAt
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, Instant> decidedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#datasetId
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#previousRetentionDays
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, Integer> previousRetentionDays;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#requestedRetentionDays
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, Integer> requestedRetentionDays;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#id
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest
	 **/
	public static volatile EntityType<CatalogLifecycleRequest> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#ownerDept
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#requestedExpiresAt
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, Instant> requestedExpiresAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogLifecycleRequest#status
	 **/
	public static volatile SingularAttribute<CatalogLifecycleRequest, String> status;

}

