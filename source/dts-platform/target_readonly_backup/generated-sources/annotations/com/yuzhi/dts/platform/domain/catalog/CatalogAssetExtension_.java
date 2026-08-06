package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogAssetExtension.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogAssetExtension_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String LIFECYCLE_STATUS = "lifecycleStatus";
	public static final String CLASSIFICATION = "classification";
	public static final String WAREHOUSE_LAYER = "warehouseLayer";
	public static final String GOVERNANCE_STATUS = "governanceStatus";
	public static final String DOMAIN_ID = "domainId";
	public static final String ENABLED = "enabled";
	public static final String OM_ASSET = "omAsset";
	public static final String SECURITY_POLICY_REFS = "securityPolicyRefs";
	public static final String LEGACY_DATASET_ID = "legacyDatasetId";
	public static final String ID = "id";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String BUSINESS_OWNER = "businessOwner";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension#lifecycleStatus
	 **/
	public static volatile SingularAttribute<CatalogAssetExtension, String> lifecycleStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension#classification
	 **/
	public static volatile SingularAttribute<CatalogAssetExtension, String> classification;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension#warehouseLayer
	 **/
	public static volatile SingularAttribute<CatalogAssetExtension, String> warehouseLayer;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension#governanceStatus
	 **/
	public static volatile SingularAttribute<CatalogAssetExtension, String> governanceStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension#domainId
	 **/
	public static volatile SingularAttribute<CatalogAssetExtension, UUID> domainId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension#enabled
	 **/
	public static volatile SingularAttribute<CatalogAssetExtension, Boolean> enabled;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension#omAsset
	 **/
	public static volatile SingularAttribute<CatalogAssetExtension, OpenMetadataAssetCache> omAsset;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension#securityPolicyRefs
	 **/
	public static volatile SingularAttribute<CatalogAssetExtension, String> securityPolicyRefs;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension#legacyDatasetId
	 **/
	public static volatile SingularAttribute<CatalogAssetExtension, UUID> legacyDatasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension#id
	 **/
	public static volatile SingularAttribute<CatalogAssetExtension, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension
	 **/
	public static volatile EntityType<CatalogAssetExtension> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension#ownerDept
	 **/
	public static volatile SingularAttribute<CatalogAssetExtension, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetExtension#businessOwner
	 **/
	public static volatile SingularAttribute<CatalogAssetExtension, String> businessOwner;

}

