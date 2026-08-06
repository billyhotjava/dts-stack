package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogTagCategory.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogTagCategory_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String CODE = "code";
	public static final String SORT_ORDER = "sortOrder";
	public static final String BUILTIN = "builtin";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String ID = "id";
	public static final String PARENT_ID = "parentId";
	public static final String ENABLED = "enabled";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory#code
	 **/
	public static volatile SingularAttribute<CatalogTagCategory, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory#sortOrder
	 **/
	public static volatile SingularAttribute<CatalogTagCategory, Integer> sortOrder;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory#builtin
	 **/
	public static volatile SingularAttribute<CatalogTagCategory, Boolean> builtin;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory#name
	 **/
	public static volatile SingularAttribute<CatalogTagCategory, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory#description
	 **/
	public static volatile SingularAttribute<CatalogTagCategory, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory#id
	 **/
	public static volatile SingularAttribute<CatalogTagCategory, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory
	 **/
	public static volatile EntityType<CatalogTagCategory> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory#parentId
	 **/
	public static volatile SingularAttribute<CatalogTagCategory, UUID> parentId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTagCategory#enabled
	 **/
	public static volatile SingularAttribute<CatalogTagCategory, Boolean> enabled;

}

