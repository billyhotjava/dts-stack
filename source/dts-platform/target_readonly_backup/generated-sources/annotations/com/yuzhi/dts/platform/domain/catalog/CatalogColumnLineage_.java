package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogColumnLineage.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogColumnLineage_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String RELATION_TYPE = "relationType";
	public static final String DATASET_LINEAGE_ID = "datasetLineageId";
	public static final String EXPRESSION = "expression";
	public static final String UPSTREAM_DATASET_ID = "upstreamDatasetId";
	public static final String UPSTREAM_COLUMN_ID = "upstreamColumnId";
	public static final String LAST_OBSERVED_AT = "lastObservedAt";
	public static final String CONFIDENCE = "confidence";
	public static final String DOWNSTREAM_DATASET_ID = "downstreamDatasetId";
	public static final String DOWNSTREAM_COLUMN_ID = "downstreamColumnId";
	public static final String VALID_FROM = "validFrom";
	public static final String LINEAGE_JOB_ID = "lineageJobId";
	public static final String LINEAGE_TYPE = "lineageType";
	public static final String DOWNSTREAM_COLUMN = "downstreamColumn";
	public static final String ID = "id";
	public static final String PROJECT_NAME = "projectName";
	public static final String UPSTREAM_COLUMN = "upstreamColumn";
	public static final String VALID_TO = "validTo";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#relationType
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, String> relationType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#datasetLineageId
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, UUID> datasetLineageId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#expression
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, String> expression;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#upstreamDatasetId
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, UUID> upstreamDatasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#upstreamColumnId
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, UUID> upstreamColumnId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#lastObservedAt
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, Instant> lastObservedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#confidence
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, String> confidence;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#downstreamDatasetId
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, UUID> downstreamDatasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#downstreamColumnId
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, UUID> downstreamColumnId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#validFrom
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, Instant> validFrom;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#lineageJobId
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, UUID> lineageJobId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#lineageType
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, String> lineageType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#downstreamColumn
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, String> downstreamColumn;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#id
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#projectName
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, String> projectName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage
	 **/
	public static volatile EntityType<CatalogColumnLineage> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#upstreamColumn
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, String> upstreamColumn;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage#validTo
	 **/
	public static volatile SingularAttribute<CatalogColumnLineage, Instant> validTo;

}

