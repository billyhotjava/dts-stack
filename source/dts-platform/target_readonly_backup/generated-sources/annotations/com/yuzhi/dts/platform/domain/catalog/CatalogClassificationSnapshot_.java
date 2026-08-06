package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogClassificationSnapshot.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogClassificationSnapshot_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SEALED_AT = "sealedAt";
	public static final String SUBJECT_TYPE = "subjectType";
	public static final String PROPAGATION_STATUS = "propagationStatus";
	public static final String EFFECTIVE_LEVEL = "effectiveLevel";
	public static final String ASSET_TYPE = "assetType";
	public static final String ORIGIN_TYPE = "originType";
	public static final String ORIGIN_REF = "originRef";
	public static final String RECORD_VERSION = "recordVersion";
	public static final String DECLARED_LEVEL = "declaredLevel";
	public static final String DETECTED_LEVEL = "detectedLevel";
	public static final String EVIDENCE_CHECKSUM = "evidenceChecksum";
	public static final String ID = "id";
	public static final String MANUAL_FLOOR = "manualFloor";
	public static final String SUBJECT_KEY = "subjectKey";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot#sealedAt
	 **/
	public static volatile SingularAttribute<CatalogClassificationSnapshot, Instant> sealedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot#subjectType
	 **/
	public static volatile SingularAttribute<CatalogClassificationSnapshot, String> subjectType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot#propagationStatus
	 **/
	public static volatile SingularAttribute<CatalogClassificationSnapshot, String> propagationStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot#effectiveLevel
	 **/
	public static volatile SingularAttribute<CatalogClassificationSnapshot, String> effectiveLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot#assetType
	 **/
	public static volatile SingularAttribute<CatalogClassificationSnapshot, String> assetType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot#originType
	 **/
	public static volatile SingularAttribute<CatalogClassificationSnapshot, String> originType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot#originRef
	 **/
	public static volatile SingularAttribute<CatalogClassificationSnapshot, String> originRef;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot#recordVersion
	 **/
	public static volatile SingularAttribute<CatalogClassificationSnapshot, Long> recordVersion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot#declaredLevel
	 **/
	public static volatile SingularAttribute<CatalogClassificationSnapshot, String> declaredLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot#detectedLevel
	 **/
	public static volatile SingularAttribute<CatalogClassificationSnapshot, String> detectedLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot#evidenceChecksum
	 **/
	public static volatile SingularAttribute<CatalogClassificationSnapshot, String> evidenceChecksum;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot#id
	 **/
	public static volatile SingularAttribute<CatalogClassificationSnapshot, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot#manualFloor
	 **/
	public static volatile SingularAttribute<CatalogClassificationSnapshot, String> manualFloor;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot
	 **/
	public static volatile EntityType<CatalogClassificationSnapshot> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot#subjectKey
	 **/
	public static volatile SingularAttribute<CatalogClassificationSnapshot, String> subjectKey;

}

