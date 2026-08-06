package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogTag.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogTag_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String CODE = "code";
	public static final String COLOR = "color";
	public static final String BUILTIN = "builtin";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String ID = "id";
	public static final String CATEGORY_ID = "categoryId";
	public static final String ENABLED = "enabled";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTag#code
	 **/
	public static volatile SingularAttribute<CatalogTag, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTag#color
	 **/
	public static volatile SingularAttribute<CatalogTag, String> color;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTag#builtin
	 **/
	public static volatile SingularAttribute<CatalogTag, Boolean> builtin;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTag#name
	 **/
	public static volatile SingularAttribute<CatalogTag, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTag#description
	 **/
	public static volatile SingularAttribute<CatalogTag, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTag#id
	 **/
	public static volatile SingularAttribute<CatalogTag, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTag
	 **/
	public static volatile EntityType<CatalogTag> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTag#categoryId
	 **/
	public static volatile SingularAttribute<CatalogTag, UUID> categoryId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTag#enabled
	 **/
	public static volatile SingularAttribute<CatalogTag, Boolean> enabled;

}

