package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogDataset.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogDataset_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String OWNER = "owner";
	public static final String EXPOSED_BY = "exposedBy";
	public static final String SHARE_SCOPE = "shareScope";
	public static final String TYPE = "type";
	public static final String CLASSIFICATION = "classification";
	public static final String TAGS = "tags";
	public static final String HIVE_TABLE = "hiveTable";
	public static final String TRINO_CATALOG = "trinoCatalog";
	public static final String DOMAIN = "domain";
	public static final String SCOPE = "scope";
	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String DATA_LEVEL = "dataLevel";
	public static final String HIVE_DATABASE = "hiveDatabase";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#owner
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> owner;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#exposedBy
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> exposedBy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#shareScope
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> shareScope;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#type
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> type;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#classification
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> classification;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#tags
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> tags;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#hiveTable
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> hiveTable;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#trinoCatalog
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> trinoCatalog;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#domain
	 **/
	public static volatile SingularAttribute<CatalogDataset, CatalogDomain> domain;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#scope
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> scope;
	
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
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#dataLevel
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> dataLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDataset#hiveDatabase
	 **/
	public static volatile SingularAttribute<CatalogDataset, String> hiveDatabase;

}

