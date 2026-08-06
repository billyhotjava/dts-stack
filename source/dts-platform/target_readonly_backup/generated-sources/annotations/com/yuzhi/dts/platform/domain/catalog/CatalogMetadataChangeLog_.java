package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogMetadataChangeLog.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogMetadataChangeLog_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String AFTER_VALUE = "afterValue";
	public static final String FIELD_NAME = "fieldName";
	public static final String BEFORE_VALUE = "beforeValue";
	public static final String DATASET_ID = "datasetId";
	public static final String TABLE_ID = "tableId";
	public static final String ID = "id";
	public static final String SOURCE = "source";
	public static final String ACTOR_DEPT = "actorDept";
	public static final String CHANGE_SUMMARY = "changeSummary";
	public static final String OBJECT_ID = "objectId";
	public static final String OBJECT_TYPE = "objectType";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMetadataChangeLog#afterValue
	 **/
	public static volatile SingularAttribute<CatalogMetadataChangeLog, String> afterValue;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMetadataChangeLog#fieldName
	 **/
	public static volatile SingularAttribute<CatalogMetadataChangeLog, String> fieldName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMetadataChangeLog#beforeValue
	 **/
	public static volatile SingularAttribute<CatalogMetadataChangeLog, String> beforeValue;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMetadataChangeLog#datasetId
	 **/
	public static volatile SingularAttribute<CatalogMetadataChangeLog, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMetadataChangeLog#tableId
	 **/
	public static volatile SingularAttribute<CatalogMetadataChangeLog, UUID> tableId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMetadataChangeLog#id
	 **/
	public static volatile SingularAttribute<CatalogMetadataChangeLog, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMetadataChangeLog#source
	 **/
	public static volatile SingularAttribute<CatalogMetadataChangeLog, String> source;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMetadataChangeLog#actorDept
	 **/
	public static volatile SingularAttribute<CatalogMetadataChangeLog, String> actorDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMetadataChangeLog
	 **/
	public static volatile EntityType<CatalogMetadataChangeLog> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMetadataChangeLog#changeSummary
	 **/
	public static volatile SingularAttribute<CatalogMetadataChangeLog, String> changeSummary;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMetadataChangeLog#objectId
	 **/
	public static volatile SingularAttribute<CatalogMetadataChangeLog, UUID> objectId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogMetadataChangeLog#objectType
	 **/
	public static volatile SingularAttribute<CatalogMetadataChangeLog, String> objectType;

}

