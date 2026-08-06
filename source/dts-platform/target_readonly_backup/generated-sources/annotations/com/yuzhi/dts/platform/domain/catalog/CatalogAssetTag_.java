package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogAssetTag.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogAssetTag_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String TAGGED_BY = "taggedBy";
	public static final String TAG_ID = "tagId";
	public static final String ID = "id";
	public static final String MIGRATION_BATCH = "migrationBatch";
	public static final String TAGGED_AT = "taggedAt";
	public static final String ASSET_KEY = "assetKey";
	public static final String ASSET_TYPE = "assetType";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetTag#taggedBy
	 **/
	public static volatile SingularAttribute<CatalogAssetTag, String> taggedBy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetTag#tagId
	 **/
	public static volatile SingularAttribute<CatalogAssetTag, UUID> tagId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetTag#id
	 **/
	public static volatile SingularAttribute<CatalogAssetTag, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetTag#migrationBatch
	 **/
	public static volatile SingularAttribute<CatalogAssetTag, String> migrationBatch;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetTag
	 **/
	public static volatile EntityType<CatalogAssetTag> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetTag#taggedAt
	 **/
	public static volatile SingularAttribute<CatalogAssetTag, Instant> taggedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetTag#assetKey
	 **/
	public static volatile SingularAttribute<CatalogAssetTag, String> assetKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetTag#assetType
	 **/
	public static volatile SingularAttribute<CatalogAssetTag, String> assetType;

}

