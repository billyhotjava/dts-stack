package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogClassificationMapping.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogClassificationMapping_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String ID = "id";
	public static final String SOURCE = "source";
	public static final String SOURCE_LEVEL = "sourceLevel";
	public static final String PLATFORM_LEVEL = "platformLevel";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationMapping#id
	 **/
	public static volatile SingularAttribute<CatalogClassificationMapping, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationMapping#source
	 **/
	public static volatile SingularAttribute<CatalogClassificationMapping, String> source;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationMapping#sourceLevel
	 **/
	public static volatile SingularAttribute<CatalogClassificationMapping, String> sourceLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationMapping
	 **/
	public static volatile EntityType<CatalogClassificationMapping> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationMapping#platformLevel
	 **/
	public static volatile SingularAttribute<CatalogClassificationMapping, String> platformLevel;

}

