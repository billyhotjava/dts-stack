package com.yuzhi.dts.admin.domain;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(PortalMenu.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class PortalMenu_ extends com.yuzhi.dts.admin.domain.AbstractAuditingEntity_ {

	public static final String PARENT = "parent";
	public static final String METADATA = "metadata";
	public static final String ICON = "icon";
	public static final String SECURITY_LEVEL = "securityLevel";
	public static final String PATH = "path";
	public static final String COMPONENT = "component";
	public static final String DELETED = "deleted";
	public static final String CHILDREN = "children";
	public static final String SORT_ORDER = "sortOrder";
	public static final String NAME = "name";
	public static final String VISIBILITIES = "visibilities";
	public static final String ID = "id";

	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenu#parent
	 **/
	public static volatile SingularAttribute<PortalMenu, PortalMenu> parent;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenu#metadata
	 **/
	public static volatile SingularAttribute<PortalMenu, String> metadata;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenu#icon
	 **/
	public static volatile SingularAttribute<PortalMenu, String> icon;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenu#securityLevel
	 **/
	public static volatile SingularAttribute<PortalMenu, String> securityLevel;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenu#path
	 **/
	public static volatile SingularAttribute<PortalMenu, String> path;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenu#component
	 **/
	public static volatile SingularAttribute<PortalMenu, String> component;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenu#deleted
	 **/
	public static volatile SingularAttribute<PortalMenu, Boolean> deleted;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenu#children
	 **/
	public static volatile ListAttribute<PortalMenu, PortalMenu> children;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenu#sortOrder
	 **/
	public static volatile SingularAttribute<PortalMenu, Integer> sortOrder;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenu#name
	 **/
	public static volatile SingularAttribute<PortalMenu, String> name;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenu#visibilities
	 **/
	public static volatile ListAttribute<PortalMenu, PortalMenuVisibility> visibilities;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenu#id
	 **/
	public static volatile SingularAttribute<PortalMenu, Long> id;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.PortalMenu
	 **/
	public static volatile EntityType<PortalMenu> class_;

}

