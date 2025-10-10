package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogAccessPolicy.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogAccessPolicy_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String ALLOW_ROLES = "allowRoles";
	public static final String ID = "id";
	public static final String DATASET = "dataset";
	public static final String ROW_FILTER = "rowFilter";
	public static final String DEFAULT_MASKING = "defaultMasking";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy#allowRoles
	 **/
	public static volatile SingularAttribute<CatalogAccessPolicy, String> allowRoles;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy#id
	 **/
	public static volatile SingularAttribute<CatalogAccessPolicy, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy
	 **/
	public static volatile EntityType<CatalogAccessPolicy> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy#dataset
	 **/
	public static volatile SingularAttribute<CatalogAccessPolicy, CatalogDataset> dataset;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy#rowFilter
	 **/
	public static volatile SingularAttribute<CatalogAccessPolicy, String> rowFilter;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy#defaultMasking
	 **/
	public static volatile SingularAttribute<CatalogAccessPolicy, String> defaultMasking;

}

