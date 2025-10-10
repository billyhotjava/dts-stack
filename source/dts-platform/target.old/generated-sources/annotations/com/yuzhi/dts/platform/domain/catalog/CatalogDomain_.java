package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogDomain.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogDomain_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String OWNER = "owner";
	public static final String PARENT = "parent";
	public static final String CODE = "code";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String ID = "id";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDomain#owner
	 **/
	public static volatile SingularAttribute<CatalogDomain, String> owner;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDomain#parent
	 **/
	public static volatile SingularAttribute<CatalogDomain, CatalogDomain> parent;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDomain#code
	 **/
	public static volatile SingularAttribute<CatalogDomain, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDomain#name
	 **/
	public static volatile SingularAttribute<CatalogDomain, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDomain#description
	 **/
	public static volatile SingularAttribute<CatalogDomain, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDomain#id
	 **/
	public static volatile SingularAttribute<CatalogDomain, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDomain
	 **/
	public static volatile EntityType<CatalogDomain> class_;

}

