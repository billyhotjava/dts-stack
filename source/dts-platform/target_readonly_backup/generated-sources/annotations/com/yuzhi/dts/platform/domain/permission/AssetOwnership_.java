package com.yuzhi.dts.platform.domain.permission;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(AssetOwnership.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class AssetOwnership_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SOURCE_ID = "sourceId";
	public static final String ASSIGNED_BY = "assignedBy";
	public static final String ASSET_ID = "assetId";
	public static final String OWNER_DEPT_CODE = "ownerDeptCode";
	public static final String ID = "id";
	public static final String ASSET_TYPE = "assetType";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetOwnership#sourceId
	 **/
	public static volatile SingularAttribute<AssetOwnership, String> sourceId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetOwnership#assignedBy
	 **/
	public static volatile SingularAttribute<AssetOwnership, String> assignedBy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetOwnership#assetId
	 **/
	public static volatile SingularAttribute<AssetOwnership, String> assetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetOwnership#ownerDeptCode
	 **/
	public static volatile SingularAttribute<AssetOwnership, String> ownerDeptCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetOwnership#id
	 **/
	public static volatile SingularAttribute<AssetOwnership, Long> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetOwnership
	 **/
	public static volatile EntityType<AssetOwnership> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetOwnership#assetType
	 **/
	public static volatile SingularAttribute<AssetOwnership, String> assetType;

}

