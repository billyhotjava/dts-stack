package com.yuzhi.dts.admin.domain;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(PortalMenuVisibility.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class PortalMenuVisibility_ extends com.yuzhi.dts.admin.domain.AbstractAuditingEntity_ {

	public static final String ROLE_CODE = "roleCode";
	public static final String ID = "id";
	public static final String MENU = "menu";
	public static final String PERMISSION_CODE = "permissionCode";
	public static final String DATA_LEVEL = "dataLevel";

	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenuVisibility#roleCode
	 **/
	public static volatile SingularAttribute<PortalMenuVisibility, String> roleCode;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenuVisibility#id
	 **/
	public static volatile SingularAttribute<PortalMenuVisibility, Long> id;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenuVisibility#menu
	 **/
	public static volatile SingularAttribute<PortalMenuVisibility, PortalMenu> menu;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenuVisibility#permissionCode
	 **/
	public static volatile SingularAttribute<PortalMenuVisibility, String> permissionCode;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenuVisibility
	 **/
	public static volatile EntityType<PortalMenuVisibility> class_;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenuVisibility#dataLevel
	 **/
	public static volatile SingularAttribute<PortalMenuVisibility, String> dataLevel;

}

