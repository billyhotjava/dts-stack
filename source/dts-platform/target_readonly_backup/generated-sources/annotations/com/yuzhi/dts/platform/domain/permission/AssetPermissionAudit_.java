package com.yuzhi.dts.platform.domain.permission;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;

@StaticMetamodel(AssetPermissionAudit.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class AssetPermissionAudit_ {

	public static final String PERMISSION = "permission";
	public static final String OPERATOR = "operator";
	public static final String ASSET_TYPE = "assetType";
	public static final String OA_REFERENCE = "oaReference";
	public static final String REASON_DETAIL = "reasonDetail";
	public static final String CREATED_DATE = "createdDate";
	public static final String ASSET_ID = "assetId";
	public static final String ACTION = "action";
	public static final String ID = "id";
	public static final String REASON_CODE = "reasonCode";
	public static final String DETAIL = "detail";
	public static final String TARGET_USER = "targetUser";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit#permission
	 **/
	public static volatile SingularAttribute<AssetPermissionAudit, String> permission;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit#operator
	 **/
	public static volatile SingularAttribute<AssetPermissionAudit, String> operator;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit#assetType
	 **/
	public static volatile SingularAttribute<AssetPermissionAudit, String> assetType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit#oaReference
	 **/
	public static volatile SingularAttribute<AssetPermissionAudit, String> oaReference;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit#reasonDetail
	 **/
	public static volatile SingularAttribute<AssetPermissionAudit, String> reasonDetail;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit#createdDate
	 **/
	public static volatile SingularAttribute<AssetPermissionAudit, Instant> createdDate;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit#assetId
	 **/
	public static volatile SingularAttribute<AssetPermissionAudit, String> assetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit#action
	 **/
	public static volatile SingularAttribute<AssetPermissionAudit, String> action;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit#id
	 **/
	public static volatile SingularAttribute<AssetPermissionAudit, Long> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit#reasonCode
	 **/
	public static volatile SingularAttribute<AssetPermissionAudit, String> reasonCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit#detail
	 **/
	public static volatile SingularAttribute<AssetPermissionAudit, String> detail;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit
	 **/
	public static volatile EntityType<AssetPermissionAudit> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionAudit#targetUser
	 **/
	public static volatile SingularAttribute<AssetPermissionAudit, String> targetUser;

}

