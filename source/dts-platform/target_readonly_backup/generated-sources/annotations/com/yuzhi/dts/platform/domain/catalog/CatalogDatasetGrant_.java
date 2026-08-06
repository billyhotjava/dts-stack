package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogDatasetGrant.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogDatasetGrant_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String GRANTEE_DEPT = "granteeDept";
	public static final String GRANTEE_ID = "granteeId";
	public static final String CAN_QUERY = "canQuery";
	public static final String VALID_FROM = "validFrom";
	public static final String GRANTEE_NAME = "granteeName";
	public static final String CAN_PREVIEW = "canPreview";
	public static final String GRANTEE_USERNAME = "granteeUsername";
	public static final String ID = "id";
	public static final String GRANT_TYPE = "grantType";
	public static final String DATASET = "dataset";
	public static final String SOURCE_REQUEST_ID = "sourceRequestId";
	public static final String VALID_TO = "validTo";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant#granteeDept
	 **/
	public static volatile SingularAttribute<CatalogDatasetGrant, String> granteeDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant#granteeId
	 **/
	public static volatile SingularAttribute<CatalogDatasetGrant, String> granteeId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant#canQuery
	 **/
	public static volatile SingularAttribute<CatalogDatasetGrant, Boolean> canQuery;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant#validFrom
	 **/
	public static volatile SingularAttribute<CatalogDatasetGrant, Instant> validFrom;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant#granteeName
	 **/
	public static volatile SingularAttribute<CatalogDatasetGrant, String> granteeName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant#canPreview
	 **/
	public static volatile SingularAttribute<CatalogDatasetGrant, Boolean> canPreview;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant#granteeUsername
	 **/
	public static volatile SingularAttribute<CatalogDatasetGrant, String> granteeUsername;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant#id
	 **/
	public static volatile SingularAttribute<CatalogDatasetGrant, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant#grantType
	 **/
	public static volatile SingularAttribute<CatalogDatasetGrant, String> grantType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant
	 **/
	public static volatile EntityType<CatalogDatasetGrant> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant#dataset
	 **/
	public static volatile SingularAttribute<CatalogDatasetGrant, CatalogDataset> dataset;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant#sourceRequestId
	 **/
	public static volatile SingularAttribute<CatalogDatasetGrant, UUID> sourceRequestId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetGrant#validTo
	 **/
	public static volatile SingularAttribute<CatalogDatasetGrant, Instant> validTo;

}

