package com.yuzhi.dts.platform.domain.service;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(SvcDataProductVersion.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SvcDataProductVersion_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String DIFF_SUMMARY = "diffSummary";
	public static final String RELEASED_AT = "releasedAt";
	public static final String PRODUCT_ID = "productId";
	public static final String METADATA_JSON = "metadataJson";
	public static final String SCHEMA_JSON = "schemaJson";
	public static final String ID = "id";
	public static final String VERSION = "version";
	public static final String STATUS = "status";
	public static final String CONSUMPTION_JSON = "consumptionJson";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductVersion#diffSummary
	 **/
	public static volatile SingularAttribute<SvcDataProductVersion, String> diffSummary;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductVersion#releasedAt
	 **/
	public static volatile SingularAttribute<SvcDataProductVersion, Instant> releasedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductVersion#productId
	 **/
	public static volatile SingularAttribute<SvcDataProductVersion, UUID> productId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductVersion#metadataJson
	 **/
	public static volatile SingularAttribute<SvcDataProductVersion, String> metadataJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductVersion#schemaJson
	 **/
	public static volatile SingularAttribute<SvcDataProductVersion, String> schemaJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductVersion#id
	 **/
	public static volatile SingularAttribute<SvcDataProductVersion, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductVersion
	 **/
	public static volatile EntityType<SvcDataProductVersion> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductVersion#version
	 **/
	public static volatile SingularAttribute<SvcDataProductVersion, String> version;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductVersion#status
	 **/
	public static volatile SingularAttribute<SvcDataProductVersion, String> status;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductVersion#consumptionJson
	 **/
	public static volatile SingularAttribute<SvcDataProductVersion, String> consumptionJson;

}

