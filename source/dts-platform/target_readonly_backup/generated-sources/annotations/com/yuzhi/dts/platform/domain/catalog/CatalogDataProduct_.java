package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogDataProduct.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogDataProduct_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String FRESHNESS_SLA = "freshnessSla";
	public static final String CODE = "code";
	public static final String INDICATOR_CODES = "indicatorCodes";
	public static final String LIFECYCLE_STATUS = "lifecycleStatus";
	public static final String VISIBILITY = "visibility";
	public static final String DATASET_IDS = "datasetIds";
	public static final String CONSUMER_ENTRY = "consumerEntry";
	public static final String DESCRIPTION = "description";
	public static final String CLASSIFICATION = "classification";
	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct#freshnessSla
	 **/
	public static volatile SingularAttribute<CatalogDataProduct, String> freshnessSla;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct#code
	 **/
	public static volatile SingularAttribute<CatalogDataProduct, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct#indicatorCodes
	 **/
	public static volatile SingularAttribute<CatalogDataProduct, String> indicatorCodes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct#lifecycleStatus
	 **/
	public static volatile SingularAttribute<CatalogDataProduct, String> lifecycleStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct#visibility
	 **/
	public static volatile SingularAttribute<CatalogDataProduct, String> visibility;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct#datasetIds
	 **/
	public static volatile SingularAttribute<CatalogDataProduct, String> datasetIds;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct#consumerEntry
	 **/
	public static volatile SingularAttribute<CatalogDataProduct, String> consumerEntry;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct#description
	 **/
	public static volatile SingularAttribute<CatalogDataProduct, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct#classification
	 **/
	public static volatile SingularAttribute<CatalogDataProduct, String> classification;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct#name
	 **/
	public static volatile SingularAttribute<CatalogDataProduct, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct#id
	 **/
	public static volatile SingularAttribute<CatalogDataProduct, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct
	 **/
	public static volatile EntityType<CatalogDataProduct> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct#ownerDept
	 **/
	public static volatile SingularAttribute<CatalogDataProduct, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataProduct#status
	 **/
	public static volatile SingularAttribute<CatalogDataProduct, String> status;

}

