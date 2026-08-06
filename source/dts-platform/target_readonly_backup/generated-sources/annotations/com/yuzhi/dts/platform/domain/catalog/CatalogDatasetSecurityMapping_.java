package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogDatasetSecurityMapping.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogDatasetSecurityMapping_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String DATA_LEVEL_FIELD = "dataLevelField";
	public static final String DATASET_ID = "datasetId";
	public static final String DEPT_FIELD = "deptField";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetSecurityMapping#dataLevelField
	 **/
	public static volatile SingularAttribute<CatalogDatasetSecurityMapping, String> dataLevelField;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetSecurityMapping#datasetId
	 **/
	public static volatile SingularAttribute<CatalogDatasetSecurityMapping, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetSecurityMapping#deptField
	 **/
	public static volatile SingularAttribute<CatalogDatasetSecurityMapping, String> deptField;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetSecurityMapping
	 **/
	public static volatile EntityType<CatalogDatasetSecurityMapping> class_;

}

