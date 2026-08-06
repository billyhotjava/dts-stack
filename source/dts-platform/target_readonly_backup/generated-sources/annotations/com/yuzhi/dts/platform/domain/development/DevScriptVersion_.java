package com.yuzhi.dts.platform.domain.development;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(DevScriptVersion.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class DevScriptVersion_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String VERSION_NO = "versionNo";
	public static final String ID = "id";
	public static final String ASSET = "asset";
	public static final String CHANGE_SUMMARY = "changeSummary";
	public static final String CONTENT = "content";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptVersion#versionNo
	 **/
	public static volatile SingularAttribute<DevScriptVersion, Integer> versionNo;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptVersion#id
	 **/
	public static volatile SingularAttribute<DevScriptVersion, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptVersion#asset
	 **/
	public static volatile SingularAttribute<DevScriptVersion, DevScriptAsset> asset;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptVersion
	 **/
	public static volatile EntityType<DevScriptVersion> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptVersion#changeSummary
	 **/
	public static volatile SingularAttribute<DevScriptVersion, String> changeSummary;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptVersion#content
	 **/
	public static volatile SingularAttribute<DevScriptVersion, String> content;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptVersion#status
	 **/
	public static volatile SingularAttribute<DevScriptVersion, String> status;

}

