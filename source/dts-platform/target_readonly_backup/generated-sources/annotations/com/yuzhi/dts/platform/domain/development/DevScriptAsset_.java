package com.yuzhi.dts.platform.domain.development;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(DevScriptAsset.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class DevScriptAsset_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String LATEST_VERSION_NO = "latestVersionNo";
	public static final String SCRIPT_TYPE = "scriptType";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String ID = "id";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String ENABLED = "enabled";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptAsset#latestVersionNo
	 **/
	public static volatile SingularAttribute<DevScriptAsset, Integer> latestVersionNo;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptAsset#scriptType
	 **/
	public static volatile SingularAttribute<DevScriptAsset, String> scriptType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptAsset#name
	 **/
	public static volatile SingularAttribute<DevScriptAsset, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptAsset#description
	 **/
	public static volatile SingularAttribute<DevScriptAsset, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptAsset#id
	 **/
	public static volatile SingularAttribute<DevScriptAsset, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptAsset
	 **/
	public static volatile EntityType<DevScriptAsset> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptAsset#ownerDept
	 **/
	public static volatile SingularAttribute<DevScriptAsset, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptAsset#enabled
	 **/
	public static volatile SingularAttribute<DevScriptAsset, Boolean> enabled;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptAsset#status
	 **/
	public static volatile SingularAttribute<DevScriptAsset, String> status;

}

