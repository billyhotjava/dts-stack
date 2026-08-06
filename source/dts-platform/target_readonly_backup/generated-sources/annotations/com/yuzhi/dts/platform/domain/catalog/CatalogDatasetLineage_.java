package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogDatasetLineage.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogDatasetLineage_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String RELATION_TYPE = "relationType";
	public static final String NOTES = "notes";
	public static final String UPSTREAM_DATASET_ID = "upstreamDatasetId";
	public static final String VERIFICATION_STATUS = "verificationStatus";
	public static final String LAST_OBSERVED_AT = "lastObservedAt";
	public static final String DOWNSTREAM_DATASET_ID = "downstreamDatasetId";
	public static final String VALID_FROM = "validFrom";
	public static final String LINEAGE_JOB_ID = "lineageJobId";
	public static final String UPSTREAM_ASSET_TYPE = "upstreamAssetType";
	public static final String LAST_EXECUTION_STATUS = "lastExecutionStatus";
	public static final String ID = "id";
	public static final String LAST_VERIFIED_AT = "lastVerifiedAt";
	public static final String PROJECT_NAME = "projectName";
	public static final String LAST_EXECUTION_ID = "lastExecutionId";
	public static final String DOWNSTREAM_ASSET_TYPE = "downstreamAssetType";
	public static final String DIRECTION = "direction";
	public static final String VALID_TO = "validTo";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#relationType
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, String> relationType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#notes
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, String> notes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#upstreamDatasetId
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, UUID> upstreamDatasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#verificationStatus
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, String> verificationStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#lastObservedAt
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, Instant> lastObservedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#downstreamDatasetId
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, UUID> downstreamDatasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#validFrom
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, Instant> validFrom;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#lineageJobId
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, UUID> lineageJobId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#upstreamAssetType
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, String> upstreamAssetType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#lastExecutionStatus
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, String> lastExecutionStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#id
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#lastVerifiedAt
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, Instant> lastVerifiedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#projectName
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, String> projectName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#lastExecutionId
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, String> lastExecutionId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage
	 **/
	public static volatile EntityType<CatalogDatasetLineage> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#downstreamAssetType
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, String> downstreamAssetType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#direction
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, String> direction;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage#validTo
	 **/
	public static volatile SingularAttribute<CatalogDatasetLineage, Instant> validTo;

}

