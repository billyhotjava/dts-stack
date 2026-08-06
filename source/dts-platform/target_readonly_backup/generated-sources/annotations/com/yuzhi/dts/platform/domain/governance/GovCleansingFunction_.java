package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(GovCleansingFunction.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovCleansingFunction_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String CODE = "code";
	public static final String BUILTIN = "builtin";
	public static final String NAME = "name";
	public static final String SQL_EXPRESSION = "sqlExpression";
	public static final String DISPLAY_ORDER = "displayOrder";
	public static final String DESCRIPTION = "description";
	public static final String ID = "id";
	public static final String ENABLED = "enabled";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovCleansingFunction#code
	 **/
	public static volatile SingularAttribute<GovCleansingFunction, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovCleansingFunction#builtin
	 **/
	public static volatile SingularAttribute<GovCleansingFunction, Boolean> builtin;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovCleansingFunction#name
	 **/
	public static volatile SingularAttribute<GovCleansingFunction, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovCleansingFunction#sqlExpression
	 **/
	public static volatile SingularAttribute<GovCleansingFunction, String> sqlExpression;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovCleansingFunction#displayOrder
	 **/
	public static volatile SingularAttribute<GovCleansingFunction, Integer> displayOrder;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovCleansingFunction#description
	 **/
	public static volatile SingularAttribute<GovCleansingFunction, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovCleansingFunction#id
	 **/
	public static volatile SingularAttribute<GovCleansingFunction, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovCleansingFunction
	 **/
	public static volatile EntityType<GovCleansingFunction> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovCleansingFunction#enabled
	 **/
	public static volatile SingularAttribute<GovCleansingFunction, Boolean> enabled;

}

