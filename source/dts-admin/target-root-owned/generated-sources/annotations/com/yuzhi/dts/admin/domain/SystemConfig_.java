package com.yuzhi.dts.admin.domain;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(SystemConfig.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SystemConfig_ extends com.yuzhi.dts.admin.domain.AbstractAuditingEntity_ {

	public static final String DESCRIPTION = "description";
	public static final String ID = "id";
	public static final String VALUE = "value";
	public static final String KEY = "key";

	
	/**
	 * @see com.yuzhi.dts.admin.domain.SystemConfig#description
	 **/
	public static volatile SingularAttribute<SystemConfig, String> description;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.SystemConfig#id
	 **/
	public static volatile SingularAttribute<SystemConfig, Long> id;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.SystemConfig
	 **/
	public static volatile EntityType<SystemConfig> class_;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.SystemConfig#value
	 **/
	public static volatile SingularAttribute<SystemConfig, String> value;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.SystemConfig#key
	 **/
	public static volatile SingularAttribute<SystemConfig, String> key;

}

