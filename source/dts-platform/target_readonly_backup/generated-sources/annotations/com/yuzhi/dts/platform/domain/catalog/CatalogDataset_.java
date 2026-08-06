package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogDataset.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogDataset_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SOURCE_ID = "sourceId";
	public static final String OWNER = "owner";
	public static final String EXPOSED_BY = "exposedBy";
	public static final String LIFECYCLE_STATUS = "lifecycleStatus";
	public static final String DESCRIPTION = "description";
	public static final String SNAPSHOT_TIME = "snapshotTime";
	public static final String TYPE = "type";
	public static final String CLASSIFICATION = "classification";
	public static final String WAREHOUSE_LAYER = "warehouseLayer";
	public static final String ENABLED = "enabled";
	public static final String EXPIRES_AT = "expiresAt";
	public static final String TAGS = "tags";
	public static final String HIVE_TABLE = "hiveTable";
	public static final String HARVEST_STATUS = "harvestStatus";
	public static final String TRINO_CATALOG = "trinoCatalog";
	public static final String RETENTION_DAYS = "retentionDays";
	public static final String DOMAIN = "domain";
	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String HIVE_DATABASE = "hiveDatabase";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#sourceId
	 **/
	public static volatile SingularAttribute<CatalogDataset, UUID> sourceId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#owner
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> owner;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#exposedBy
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> exposedBy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#lifecycleStatus
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> lifecycleStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#description
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#snapshotTime
	 **/
	public static volatile SingularAttribute<CatalogDataset, Instant> snapshotTime;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#type
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> type;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#classification
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> classification;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#warehouseLayer
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> warehouseLayer;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#enabled
	 **/
	public static volatile SingularAttribute<CatalogDataset, Boolean> enabled;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#expiresAt
	 **/
	public static volatile SingularAttribute<CatalogDataset, Instant> expiresAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#tags
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> tags;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#hiveTable
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> hiveTable;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#harvestStatus
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> harvestStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#trinoCatalog
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> trinoCatalog;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#retentionDays
	 **/
	public static volatile SingularAttribute<CatalogDataset, Integer> retentionDays;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#domain
	 **/
	public static volatile SingularAttribute<CatalogDataset, CatalogDomain> domain;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#name
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#id
	 **/
	public static volatile SingularAttribute<CatalogDataset, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset
	 **/
	public static volatile EntityType<CatalogDataset> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#ownerDept
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#hiveDatabase
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> hiveDatabase;

}

