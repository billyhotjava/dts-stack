package com.yuzhi.dts.platform.domain.permission;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;

@StaticMetamodel(AssetGrant.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class AssetGrant_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String GRANTED_BY = "grantedBy";
	public static final String LEVEL_OVERRIDE = "levelOverride";
	public static final String GRANT_REASON = "grantReason";
	public static final String GRANTEE_ID = "granteeId";
	public static final String ASSET_ID = "assetId";
	public static final String PERMISSION = "permission";
	public static final String ID = "id";
	public static final String VALID_FROM = "validFrom";
	public static final String GRANTEE_TYPE = "granteeType";
	public static final String ASSET_TYPE = "assetType";
	public static final String VALID_TO = "validTo";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetGrant#grantedBy
	 **/
	public static volatile SingularAttribute<AssetGrant, String> grantedBy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetGrant#levelOverride
	 **/
	public static volatile SingularAttribute<AssetGrant, Boolean> levelOverride;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetGrant#grantReason
	 **/
	public static volatile SingularAttribute<AssetGrant, String> grantReason;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetGrant#granteeId
	 **/
	public static volatile SingularAttribute<AssetGrant, String> granteeId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetGrant#assetId
	 **/
	public static volatile SingularAttribute<AssetGrant, String> assetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetGrant#permission
	 **/
	public static volatile SingularAttribute<AssetGrant, String> permission;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetGrant#id
	 **/
	public static volatile SingularAttribute<AssetGrant, Long> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetGrant#validFrom
	 **/
	public static volatile SingularAttribute<AssetGrant, Instant> validFrom;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetGrant
	 **/
	public static volatile EntityType<AssetGrant> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetGrant#granteeType
	 **/
	public static volatile SingularAttribute<AssetGrant, String> granteeType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetGrant#assetType
	 **/
	public static volatile SingularAttribute<AssetGrant, String> assetType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetGrant#validTo
	 **/
	public static volatile SingularAttribute<AssetGrant, Instant> validTo;

}

