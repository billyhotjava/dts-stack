package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogRowFilterRule.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogRowFilterRule_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String EXPRESSION = "expression";
	public static final String ROLES = "roles";
	public static final String ID = "id";
	public static final String DATASET = "dataset";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule#expression
	 **/
	public static volatile SingularAttribute<CatalogRowFilterRule, String> expression;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule#roles
	 **/
	public static volatile SingularAttribute<CatalogRowFilterRule, String> roles;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule#id
	 **/
	public static volatile SingularAttribute<CatalogRowFilterRule, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule
	 **/
	public static volatile EntityType<CatalogRowFilterRule> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule#dataset
	 **/
	public static volatile SingularAttribute<CatalogRowFilterRule, CatalogDataset> dataset;

}

