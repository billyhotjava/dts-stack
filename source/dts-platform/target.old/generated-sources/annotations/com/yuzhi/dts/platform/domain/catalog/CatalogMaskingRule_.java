package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogMaskingRule.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogMaskingRule_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String ARGS = "args";
	public static final String FUNCTION = "function";
	public static final String COLUMN = "column";
	public static final String ID = "id";
	public static final String DATASET = "dataset";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule#args
	 **/
	public static volatile SingularAttribute<CatalogMaskingRule, String> args;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule#function
	 **/
	public static volatile SingularAttribute<CatalogMaskingRule, String> function;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule#column
	 **/
	public static volatile SingularAttribute<CatalogMaskingRule, String> column;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule#id
	 **/
	public static volatile SingularAttribute<CatalogMaskingRule, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule
	 **/
	public static volatile EntityType<CatalogMaskingRule> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule#dataset
	 **/
	public static volatile SingularAttribute<CatalogMaskingRule, CatalogDataset> dataset;

}

