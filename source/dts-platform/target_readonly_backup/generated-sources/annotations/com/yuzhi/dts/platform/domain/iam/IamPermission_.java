package com.yuzhi.dts.platform.domain.iam;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(IamPermission.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class IamPermission_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String RESOURCE = "resource";
	public static final String SCOPE = "scope";
	public static final String ACTION = "action";
	public static final String ID = "id";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamPermission#resource
	 **/
	public static volatile SingularAttribute<IamPermission, String> resource;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamPermission#scope
	 **/
	public static volatile SingularAttribute<IamPermission, String> scope;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamPermission#action
	 **/
	public static volatile SingularAttribute<IamPermission, String> action;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamPermission#id
	 **/
	public static volatile SingularAttribute<IamPermission, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamPermission
	 **/
	public static volatile EntityType<IamPermission> class_;

}

