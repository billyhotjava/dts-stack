package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogDatasetJob.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogDatasetJob_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SUBMITTED_BY = "submittedBy";
	public static final String DETAIL_PAYLOAD = "detailPayload";
	public static final String STARTED_AT = "startedAt";
	public static final String ID = "id";
	public static final String JOB_TYPE = "jobType";
	public static final String MESSAGE = "message";
	public static final String DATASET = "dataset";
	public static final String STATUS = "status";
	public static final String FINISHED_AT = "finishedAt";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetJob#submittedBy
	 **/
	public static volatile SingularAttribute<CatalogDatasetJob, String> submittedBy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetJob#detailPayload
	 **/
	public static volatile SingularAttribute<CatalogDatasetJob, String> detailPayload;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetJob#startedAt
	 **/
	public static volatile SingularAttribute<CatalogDatasetJob, Instant> startedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetJob#id
	 **/
	public static volatile SingularAttribute<CatalogDatasetJob, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetJob#jobType
	 **/
	public static volatile SingularAttribute<CatalogDatasetJob, String> jobType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetJob#message
	 **/
	public static volatile SingularAttribute<CatalogDatasetJob, String> message;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetJob
	 **/
	public static volatile EntityType<CatalogDatasetJob> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetJob#dataset
	 **/
	public static volatile SingularAttribute<CatalogDatasetJob, CatalogDataset> dataset;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetJob#status
	 **/
	public static volatile SingularAttribute<CatalogDatasetJob, String> status;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetJob#finishedAt
	 **/
	public static volatile SingularAttribute<CatalogDatasetJob, Instant> finishedAt;

}

