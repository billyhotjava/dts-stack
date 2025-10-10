package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(GovQualityRun.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovQualityRun_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SEVERITY = "severity";
	public static final String RULE = "rule";
	public static final String BINDING = "binding";
	public static final String STARTED_AT = "startedAt";
	public static final String MESSAGE = "message";
	public static final String FINISHED_AT = "finishedAt";
	public static final String JOB_ID = "jobId";
	public static final String METRICS_JSON = "metricsJson";
	public static final String RULE_VERSION = "ruleVersion";
	public static final String DATASET_ID = "datasetId";
	public static final String ID = "id";
	public static final String TRIGGER_TYPE = "triggerType";
	public static final String DURATION_MS = "durationMs";
	public static final String TRIGGER_REF = "triggerRef";
	public static final String DATA_LEVEL = "dataLevel";
	public static final String SCHEDULED_AT = "scheduledAt";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#severity
	 **/
	public static volatile SingularAttribute<GovQualityRun, String> severity;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#rule
	 **/
	public static volatile SingularAttribute<GovQualityRun, GovRule> rule;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#binding
	 **/
	public static volatile SingularAttribute<GovQualityRun, GovRuleBinding> binding;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#startedAt
	 **/
	public static volatile SingularAttribute<GovQualityRun, Instant> startedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#message
	 **/
	public static volatile SingularAttribute<GovQualityRun, String> message;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#finishedAt
	 **/
	public static volatile SingularAttribute<GovQualityRun, Instant> finishedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#jobId
	 **/
	public static volatile SingularAttribute<GovQualityRun, UUID> jobId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#metricsJson
	 **/
	public static volatile SingularAttribute<GovQualityRun, String> metricsJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#ruleVersion
	 **/
	public static volatile SingularAttribute<GovQualityRun, GovRuleVersion> ruleVersion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#datasetId
	 **/
	public static volatile SingularAttribute<GovQualityRun, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#id
	 **/
	public static volatile SingularAttribute<GovQualityRun, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#triggerType
	 **/
	public static volatile SingularAttribute<GovQualityRun, String> triggerType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun
	 **/
	public static volatile EntityType<GovQualityRun> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#durationMs
	 **/
	public static volatile SingularAttribute<GovQualityRun, Long> durationMs;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#triggerRef
	 **/
	public static volatile SingularAttribute<GovQualityRun, String> triggerRef;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#dataLevel
	 **/
	public static volatile SingularAttribute<GovQualityRun, String> dataLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#scheduledAt
	 **/
	public static volatile SingularAttribute<GovQualityRun, Instant> scheduledAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityRun#status
	 **/
	public static volatile SingularAttribute<GovQualityRun, String> status;

}

