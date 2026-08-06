package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(OpenMetadataColumnCache.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class OpenMetadataColumnCache_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String PROFILE_JSON = "profileJson";
	public static final String DATA_TYPE = "dataType";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String OM_COLUMN_FQN = "omColumnFqn";
	public static final String ID = "id";
	public static final String ASSET = "asset";
	public static final String ORDINAL_POSITION = "ordinalPosition";
	public static final String RAW_JSON = "rawJson";
	public static final String TAGS_JSON = "tagsJson";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache#profileJson
	 **/
	public static volatile SingularAttribute<OpenMetadataColumnCache, String> profileJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache#dataType
	 **/
	public static volatile SingularAttribute<OpenMetadataColumnCache, String> dataType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache#name
	 **/
	public static volatile SingularAttribute<OpenMetadataColumnCache, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache#description
	 **/
	public static volatile SingularAttribute<OpenMetadataColumnCache, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache#omColumnFqn
	 **/
	public static volatile SingularAttribute<OpenMetadataColumnCache, String> omColumnFqn;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache#id
	 **/
	public static volatile SingularAttribute<OpenMetadataColumnCache, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache#asset
	 **/
	public static volatile SingularAttribute<OpenMetadataColumnCache, OpenMetadataAssetCache> asset;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache#ordinalPosition
	 **/
	public static volatile SingularAttribute<OpenMetadataColumnCache, Integer> ordinalPosition;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache#rawJson
	 **/
	public static volatile SingularAttribute<OpenMetadataColumnCache, String> rawJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache
	 **/
	public static volatile EntityType<OpenMetadataColumnCache> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataColumnCache#tagsJson
	 **/
	public static volatile SingularAttribute<OpenMetadataColumnCache, String> tagsJson;

}

