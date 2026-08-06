package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogAssetMapping.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogAssetMapping_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SOURCE_ID = "sourceId";
	public static final String LAST_CHECKED_AT = "lastCheckedAt";
	public static final String FQN = "fqn";
	public static final String MATCH_STATUS = "matchStatus";
	public static final String MATCH_REASON = "matchReason";
	public static final String OM_ENTITY_ID = "omEntityId";
	public static final String LEGACY_DATASET_ID = "legacyDatasetId";
	public static final String CONFIDENCE = "confidence";
	public static final String ID = "id";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping#sourceId
	 **/
	public static volatile SingularAttribute<CatalogAssetMapping, UUID> sourceId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping#lastCheckedAt
	 **/
	public static volatile SingularAttribute<CatalogAssetMapping, Instant> lastCheckedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping#fqn
	 **/
	public static volatile SingularAttribute<CatalogAssetMapping, String> fqn;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping#matchStatus
	 **/
	public static volatile SingularAttribute<CatalogAssetMapping, String> matchStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping#matchReason
	 **/
	public static volatile SingularAttribute<CatalogAssetMapping, String> matchReason;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping#omEntityId
	 **/
	public static volatile SingularAttribute<CatalogAssetMapping, String> omEntityId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping#legacyDatasetId
	 **/
	public static volatile SingularAttribute<CatalogAssetMapping, UUID> legacyDatasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping#confidence
	 **/
	public static volatile SingularAttribute<CatalogAssetMapping, Integer> confidence;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping#id
	 **/
	public static volatile SingularAttribute<CatalogAssetMapping, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetMapping
	 **/
	public static volatile EntityType<CatalogAssetMapping> class_;

}

