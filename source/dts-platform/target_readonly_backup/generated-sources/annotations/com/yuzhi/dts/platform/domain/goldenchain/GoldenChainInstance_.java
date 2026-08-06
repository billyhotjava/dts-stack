package com.yuzhi.dts.platform.domain.goldenchain;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(GoldenChainInstance.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GoldenChainInstance_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String OWNER = "owner";
	public static final String CURRENT_STAGE = "currentStage";
	public static final String CHAIN_KEY = "chainKey";
	public static final String SOURCE_KIND = "sourceKind";
	public static final String LIFECYCLE_STATUS = "lifecycleStatus";
	public static final String SOURCE_REF_ID = "sourceRefId";
	public static final String DISPLAY_NAME = "displayName";
	public static final String SOURCE_REF_TYPE = "sourceRefType";
	public static final String ID = "id";
	public static final String ENABLED = "enabled";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance#owner
	 **/
	public static volatile SingularAttribute<GoldenChainInstance, String> owner;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance#currentStage
	 **/
	public static volatile SingularAttribute<GoldenChainInstance, GoldenChainStage> currentStage;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance#chainKey
	 **/
	public static volatile SingularAttribute<GoldenChainInstance, String> chainKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance#sourceKind
	 **/
	public static volatile SingularAttribute<GoldenChainInstance, GoldenChainSourceKind> sourceKind;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance#lifecycleStatus
	 **/
	public static volatile SingularAttribute<GoldenChainInstance, GoldenChainStageStatus> lifecycleStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance#sourceRefId
	 **/
	public static volatile SingularAttribute<GoldenChainInstance, String> sourceRefId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance#displayName
	 **/
	public static volatile SingularAttribute<GoldenChainInstance, String> displayName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance#sourceRefType
	 **/
	public static volatile SingularAttribute<GoldenChainInstance, String> sourceRefType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance#id
	 **/
	public static volatile SingularAttribute<GoldenChainInstance, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance
	 **/
	public static volatile EntityType<GoldenChainInstance> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance#enabled
	 **/
	public static volatile SingularAttribute<GoldenChainInstance, Boolean> enabled;

}

