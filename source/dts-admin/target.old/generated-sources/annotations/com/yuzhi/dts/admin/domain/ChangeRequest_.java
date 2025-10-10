package com.yuzhi.dts.admin.domain;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;

@StaticMetamodel(ChangeRequest.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class ChangeRequest_ extends com.yuzhi.dts.admin.domain.AbstractAuditingEntity_ {

	public static final String SUMMARY = "summary";
	public static final String PAYLOAD_JSON = "payloadJson";
	public static final String REASON = "reason";
	public static final String RESOURCE_ID = "resourceId";
	public static final String LAST_ERROR = "lastError";
	public static final String DECIDED_BY = "decidedBy";
	public static final String REQUESTED_BY = "requestedBy";
	public static final String REQUESTED_AT = "requestedAt";
	public static final String DECIDED_AT = "decidedAt";
	public static final String ACTION = "action";
	public static final String ID = "id";
	public static final String CATEGORY = "category";
	public static final String RESOURCE_TYPE = "resourceType";
	public static final String DIFF_JSON = "diffJson";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#summary
	 **/
	public static volatile SingularAttribute<ChangeRequest, String> summary;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#payloadJson
	 **/
	public static volatile SingularAttribute<ChangeRequest, String> payloadJson;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#reason
	 **/
	public static volatile SingularAttribute<ChangeRequest, String> reason;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#resourceId
	 **/
	public static volatile SingularAttribute<ChangeRequest, String> resourceId;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#lastError
	 **/
	public static volatile SingularAttribute<ChangeRequest, String> lastError;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#decidedBy
	 **/
	public static volatile SingularAttribute<ChangeRequest, String> decidedBy;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#requestedBy
	 **/
	public static volatile SingularAttribute<ChangeRequest, String> requestedBy;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#requestedAt
	 **/
	public static volatile SingularAttribute<ChangeRequest, Instant> requestedAt;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#decidedAt
	 **/
	public static volatile SingularAttribute<ChangeRequest, Instant> decidedAt;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#action
	 **/
	public static volatile SingularAttribute<ChangeRequest, String> action;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#id
	 **/
	public static volatile SingularAttribute<ChangeRequest, Long> id;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#category
	 **/
	public static volatile SingularAttribute<ChangeRequest, String> category;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest
	 **/
	public static volatile EntityType<ChangeRequest> class_;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#resourceType
	 **/
	public static volatile SingularAttribute<ChangeRequest, String> resourceType;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#diffJson
	 **/
	public static volatile SingularAttribute<ChangeRequest, String> diffJson;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.ChangeRequest#status
	 **/
	public static volatile SingularAttribute<ChangeRequest, String> status;

}

