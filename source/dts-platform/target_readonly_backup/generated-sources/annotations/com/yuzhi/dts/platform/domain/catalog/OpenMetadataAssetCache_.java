package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(OpenMetadataAssetCache.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class OpenMetadataAssetCache_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String FQN = "fqn";
	public static final String PROFILE_JSON = "profileJson";
	public static final String DATABASE_NAME = "databaseName";
	public static final String DISPLAY_NAME = "displayName";
	public static final String LAST_SYNCED_AT = "lastSyncedAt";
	public static final String DESCRIPTION = "description";
	public static final String COLUMN_COUNT = "columnCount";
	public static final String SERVICE_NAME = "serviceName";
	public static final String SCHEMA_NAME = "schemaName";
	public static final String RAW_JSON = "rawJson";
	public static final String TABLE_NAME = "tableName";
	public static final String OWNER_NAME = "ownerName";
	public static final String SOURCE_TYPE = "sourceType";
	public static final String OM_ENTITY_ID = "omEntityId";
	public static final String DOMAIN_NAME = "domainName";
	public static final String ID = "id";
	public static final String TAGS_JSON = "tagsJson";
	public static final String SYNC_STATUS = "syncStatus";
	public static final String SYNC_MESSAGE = "syncMessage";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#fqn
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> fqn;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#profileJson
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> profileJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#databaseName
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> databaseName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#displayName
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> displayName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#lastSyncedAt
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, Instant> lastSyncedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#description
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#columnCount
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, Integer> columnCount;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#serviceName
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> serviceName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#schemaName
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> schemaName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#rawJson
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> rawJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#tableName
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> tableName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#ownerName
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> ownerName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#sourceType
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> sourceType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#omEntityId
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> omEntityId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#domainName
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> domainName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#id
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache
	 **/
	public static volatile EntityType<OpenMetadataAssetCache> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#tagsJson
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> tagsJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#syncStatus
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> syncStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataAssetCache#syncMessage
	 **/
	public static volatile SingularAttribute<OpenMetadataAssetCache, String> syncMessage;

}

