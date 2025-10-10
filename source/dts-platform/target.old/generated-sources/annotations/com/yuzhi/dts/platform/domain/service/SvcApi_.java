package com.yuzhi.dts.platform.domain.service;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(SvcApi.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SvcApi_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String REQUEST_SCHEMA_JSON = "requestSchemaJson";
	public static final String CODE = "code";
	public static final String METHOD = "method";
	public static final String DATASET_NAME = "datasetName";
	public static final String LAST_PUBLISHED_AT = "lastPublishedAt";
	public static final String DESCRIPTION = "description";
	public static final String CLASSIFICATION = "classification";
	public static final String CURRENT_QPS = "currentQps";
	public static final String POLICY_JSON = "policyJson";
	public static final String TAGS = "tags";
	public static final String PATH = "path";
	public static final String LATEST_VERSION = "latestVersion";
	public static final String DAILY_LIMIT = "dailyLimit";
	public static final String NAME = "name";
	public static final String DATASET_ID = "datasetId";
	public static final String RESPONSE_SCHEMA_JSON = "responseSchemaJson";
	public static final String ID = "id";
	public static final String QPS_LIMIT = "qpsLimit";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#requestSchemaJson
	 **/
	public static volatile SingularAttribute<SvcApi, String> requestSchemaJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#code
	 **/
	public static volatile SingularAttribute<SvcApi, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#method
	 **/
	public static volatile SingularAttribute<SvcApi, String> method;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#datasetName
	 **/
	public static volatile SingularAttribute<SvcApi, String> datasetName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#lastPublishedAt
	 **/
	public static volatile SingularAttribute<SvcApi, Instant> lastPublishedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#description
	 **/
	public static volatile SingularAttribute<SvcApi, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#classification
	 **/
	public static volatile SingularAttribute<SvcApi, String> classification;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#currentQps
	 **/
	public static volatile SingularAttribute<SvcApi, Integer> currentQps;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#policyJson
	 **/
	public static volatile SingularAttribute<SvcApi, String> policyJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#tags
	 **/
	public static volatile SingularAttribute<SvcApi, String> tags;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#path
	 **/
	public static volatile SingularAttribute<SvcApi, String> path;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#latestVersion
	 **/
	public static volatile SingularAttribute<SvcApi, String> latestVersion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#dailyLimit
	 **/
	public static volatile SingularAttribute<SvcApi, Integer> dailyLimit;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#name
	 **/
	public static volatile SingularAttribute<SvcApi, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#datasetId
	 **/
	public static volatile SingularAttribute<SvcApi, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#responseSchemaJson
	 **/
	public static volatile SingularAttribute<SvcApi, String> responseSchemaJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#id
	 **/
	public static volatile SingularAttribute<SvcApi, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#qpsLimit
	 **/
	public static volatile SingularAttribute<SvcApi, Integer> qpsLimit;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi
	 **/
	public static volatile EntityType<SvcApi> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApi#status
	 **/
	public static volatile SingularAttribute<SvcApi, String> status;

}

