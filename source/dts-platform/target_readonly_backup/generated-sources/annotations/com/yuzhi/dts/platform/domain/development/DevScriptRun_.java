package com.yuzhi.dts.platform.domain.development;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(DevScriptRun.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class DevScriptRun_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String ERROR_MESSAGE = "errorMessage";
	public static final String STARTED_AT = "startedAt";
	public static final String FINISHED_AT = "finishedAt";
	public static final String LOG_TEXT = "logText";
	public static final String EXECUTION_ID = "executionId";
	public static final String VERSION_NO = "versionNo";
	public static final String FAILURE_TYPE = "failureType";
	public static final String ID = "id";
	public static final String ASSET = "asset";
	public static final String DURATION_MS = "durationMs";
	public static final String STATUS = "status";
	public static final String TRIGGERED_BY = "triggeredBy";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptRun#errorMessage
	 **/
	public static volatile SingularAttribute<DevScriptRun, String> errorMessage;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptRun#startedAt
	 **/
	public static volatile SingularAttribute<DevScriptRun, Instant> startedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptRun#finishedAt
	 **/
	public static volatile SingularAttribute<DevScriptRun, Instant> finishedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptRun#logText
	 **/
	public static volatile SingularAttribute<DevScriptRun, String> logText;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptRun#executionId
	 **/
	public static volatile SingularAttribute<DevScriptRun, String> executionId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptRun#versionNo
	 **/
	public static volatile SingularAttribute<DevScriptRun, Integer> versionNo;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptRun#failureType
	 **/
	public static volatile SingularAttribute<DevScriptRun, String> failureType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptRun#id
	 **/
	public static volatile SingularAttribute<DevScriptRun, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptRun#asset
	 **/
	public static volatile SingularAttribute<DevScriptRun, DevScriptAsset> asset;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptRun
	 **/
	public static volatile EntityType<DevScriptRun> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptRun#durationMs
	 **/
	public static volatile SingularAttribute<DevScriptRun, Long> durationMs;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptRun#status
	 **/
	public static volatile SingularAttribute<DevScriptRun, String> status;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.development.DevScriptRun#triggeredBy
	 **/
	public static volatile SingularAttribute<DevScriptRun, String> triggeredBy;

}

