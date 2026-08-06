package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(OpenMetadataLineageCache.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class OpenMetadataLineageCache_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String TO_OM_ENTITY_ID = "toOmEntityId";
	public static final String EDGE_TYPE = "edgeType";
	public static final String LAST_SYNCED_AT = "lastSyncedAt";
	public static final String ID = "id";
	public static final String FROM_FQN = "fromFqn";
	public static final String SOURCE = "source";
	public static final String FROM_OM_ENTITY_ID = "fromOmEntityId";
	public static final String RAW_JSON = "rawJson";
	public static final String TO_FQN = "toFqn";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataLineageCache#toOmEntityId
	 **/
	public static volatile SingularAttribute<OpenMetadataLineageCache, String> toOmEntityId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataLineageCache#edgeType
	 **/
	public static volatile SingularAttribute<OpenMetadataLineageCache, String> edgeType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataLineageCache#lastSyncedAt
	 **/
	public static volatile SingularAttribute<OpenMetadataLineageCache, Instant> lastSyncedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataLineageCache#id
	 **/
	public static volatile SingularAttribute<OpenMetadataLineageCache, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataLineageCache#fromFqn
	 **/
	public static volatile SingularAttribute<OpenMetadataLineageCache, String> fromFqn;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataLineageCache#source
	 **/
	public static volatile SingularAttribute<OpenMetadataLineageCache, String> source;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataLineageCache#fromOmEntityId
	 **/
	public static volatile SingularAttribute<OpenMetadataLineageCache, String> fromOmEntityId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataLineageCache#rawJson
	 **/
	public static volatile SingularAttribute<OpenMetadataLineageCache, String> rawJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataLineageCache
	 **/
	public static volatile EntityType<OpenMetadataLineageCache> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.OpenMetadataLineageCache#toFqn
	 **/
	public static volatile SingularAttribute<OpenMetadataLineageCache, String> toFqn;

}

