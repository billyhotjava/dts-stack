package com.yuzhi.dts.admin.domain;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(AdminCustomRole.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class AdminCustomRole_ extends com.yuzhi.dts.admin.domain.AbstractAuditingEntity_ {

	public static final String MAX_ROWS = "maxRows";
	public static final String OPERATIONS_CSV = "operationsCsv";
	public static final String SCOPE = "scope";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String ID = "id";
	public static final String ALLOW_DESENSITIZE_JSON = "allowDesensitizeJson";

	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminCustomRole#maxRows
	 **/
	public static volatile SingularAttribute<AdminCustomRole, Integer> maxRows;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminCustomRole#operationsCsv
	 **/
	public static volatile SingularAttribute<AdminCustomRole, String> operationsCsv;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminCustomRole#scope
	 **/
	public static volatile SingularAttribute<AdminCustomRole, String> scope;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminCustomRole#name
	 **/
	public static volatile SingularAttribute<AdminCustomRole, String> name;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminCustomRole#description
	 **/
	public static volatile SingularAttribute<AdminCustomRole, String> description;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminCustomRole#id
	 **/
	public static volatile SingularAttribute<AdminCustomRole, Long> id;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminCustomRole#allowDesensitizeJson
	 **/
	public static volatile SingularAttribute<AdminCustomRole, Boolean> allowDesensitizeJson;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminCustomRole
	 **/
	public static volatile EntityType<AdminCustomRole> class_;

}

