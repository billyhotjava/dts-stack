package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogSecureView.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogSecureView_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String MASK_COLUMNS = "maskColumns";
	public static final String VIEW_NAME = "viewName";
	public static final String LEVEL = "level";
	public static final String REFRESH = "refresh";
	public static final String ID = "id";
	public static final String DATASET = "dataset";
	public static final String ROW_FILTER = "rowFilter";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSecureView#maskColumns
	 **/
	public static volatile SingularAttribute<CatalogSecureView, String> maskColumns;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSecureView#viewName
	 **/
	public static volatile SingularAttribute<CatalogSecureView, String> viewName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSecureView#level
	 **/
	public static volatile SingularAttribute<CatalogSecureView, String> level;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSecureView#refresh
	 **/
	public static volatile SingularAttribute<CatalogSecureView, String> refresh;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSecureView#id
	 **/
	public static volatile SingularAttribute<CatalogSecureView, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSecureView
	 **/
	public static volatile EntityType<CatalogSecureView> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSecureView#dataset
	 **/
	public static volatile SingularAttribute<CatalogSecureView, CatalogDataset> dataset;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSecureView#rowFilter
	 **/
	public static volatile SingularAttribute<CatalogSecureView, String> rowFilter;

}

