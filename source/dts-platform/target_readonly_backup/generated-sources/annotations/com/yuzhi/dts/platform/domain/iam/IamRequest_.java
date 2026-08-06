package com.yuzhi.dts.platform.domain.iam;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(IamRequest.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class IamRequest_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String REQUESTER = "requester";
	public static final String REASON = "reason";
	public static final String RESOURCE = "resource";
	public static final String ACTION = "action";
	public static final String ID = "id";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamRequest#requester
	 **/
	public static volatile SingularAttribute<IamRequest, String> requester;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamRequest#reason
	 **/
	public static volatile SingularAttribute<IamRequest, String> reason;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamRequest#resource
	 **/
	public static volatile SingularAttribute<IamRequest, String> resource;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamRequest#action
	 **/
	public static volatile SingularAttribute<IamRequest, String> action;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamRequest#id
	 **/
	public static volatile SingularAttribute<IamRequest, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamRequest
	 **/
	public static volatile EntityType<IamRequest> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamRequest#status
	 **/
	public static volatile SingularAttribute<IamRequest, String> status;

}

