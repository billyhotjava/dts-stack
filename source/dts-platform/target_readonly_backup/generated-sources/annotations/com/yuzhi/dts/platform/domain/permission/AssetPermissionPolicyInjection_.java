package com.yuzhi.dts.platform.domain.permission;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(AssetPermissionPolicyInjection.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class AssetPermissionPolicyInjection_ {

	public static final String OCCURRED_AT = "occurredAt";
	public static final String MASKED_COLUMNS = "maskedColumns";
	public static final String POLICY_SOURCE = "policySource";
	public static final String ASSET_TYPE = "assetType";
	public static final String ACTOR = "actor";
	public static final String PACK_ID = "packId";
	public static final String PREDICATES = "predicates";
	public static final String ASSET_ID = "assetId";
	public static final String ACTION = "action";
	public static final String PREDICATE_HASH = "predicateHash";
	public static final String ID = "id";
	public static final String DIRECTION = "direction";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection#occurredAt
	 **/
	public static volatile SingularAttribute<AssetPermissionPolicyInjection, Instant> occurredAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection#maskedColumns
	 **/
	public static volatile SingularAttribute<AssetPermissionPolicyInjection, String> maskedColumns;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection#policySource
	 **/
	public static volatile SingularAttribute<AssetPermissionPolicyInjection, String> policySource;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection#assetType
	 **/
	public static volatile SingularAttribute<AssetPermissionPolicyInjection, String> assetType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection#actor
	 **/
	public static volatile SingularAttribute<AssetPermissionPolicyInjection, String> actor;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection#packId
	 **/
	public static volatile SingularAttribute<AssetPermissionPolicyInjection, String> packId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection#predicates
	 **/
	public static volatile SingularAttribute<AssetPermissionPolicyInjection, String> predicates;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection#assetId
	 **/
	public static volatile SingularAttribute<AssetPermissionPolicyInjection, String> assetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection#action
	 **/
	public static volatile SingularAttribute<AssetPermissionPolicyInjection, String> action;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection#predicateHash
	 **/
	public static volatile SingularAttribute<AssetPermissionPolicyInjection, String> predicateHash;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection#id
	 **/
	public static volatile SingularAttribute<AssetPermissionPolicyInjection, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection
	 **/
	public static volatile EntityType<AssetPermissionPolicyInjection> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.permission.AssetPermissionPolicyInjection#direction
	 **/
	public static volatile SingularAttribute<AssetPermissionPolicyInjection, String> direction;

}

