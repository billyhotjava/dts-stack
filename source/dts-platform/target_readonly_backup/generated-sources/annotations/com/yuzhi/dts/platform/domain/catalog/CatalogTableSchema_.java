package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogTableSchema.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogTableSchema_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String OWNER = "owner";
	public static final String NAME = "name";
	public static final String BIZ_DOMAIN = "bizDomain";
	public static final String ID = "id";
	public static final String CLASSIFICATION = "classification";
	public static final String DATASET = "dataset";
	public static final String TAGS = "tags";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema#owner
	 **/
	public static volatile SingularAttribute<CatalogTableSchema, String> owner;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema#name
	 **/
	public static volatile SingularAttribute<CatalogTableSchema, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema#bizDomain
	 **/
	public static volatile SingularAttribute<CatalogTableSchema, String> bizDomain;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema#id
	 **/
	public static volatile SingularAttribute<CatalogTableSchema, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema#classification
	 **/
	public static volatile SingularAttribute<CatalogTableSchema, String> classification;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema
	 **/
	public static volatile EntityType<CatalogTableSchema> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema#dataset
	 **/
	public static volatile SingularAttribute<CatalogTableSchema, CatalogDataset> dataset;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema#tags
	 **/
	public static volatile SingularAttribute<CatalogTableSchema, String> tags;

}

