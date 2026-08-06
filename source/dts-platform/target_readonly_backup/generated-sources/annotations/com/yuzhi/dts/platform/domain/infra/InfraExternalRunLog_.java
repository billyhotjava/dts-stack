package com.yuzhi.dts.platform.domain.infra;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(InfraExternalRunLog.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class InfraExternalRunLog_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String EXTERNAL_URL = "externalUrl";
	public static final String ARTIFACT_TYPE = "artifactType";
	public static final String STARTED_AT = "startedAt";
	public static final String MESSAGE = "message";
	public static final String CLASSIFICATION = "classification";
	public static final String ENABLED = "enabled";
	public static final String FINISHED_AT = "finishedAt";
	public static final String METRICS_JSON = "metricsJson";
	public static final String EXTERNAL_RUN_ID = "externalRunId";
	public static final String ARTIFACT_NAME = "artifactName";
	public static final String ENTRY_KEY = "entryKey";
	public static final String ARTIFACT_ID = "artifactId";
	public static final String ID = "id";
	public static final String DURATION_MS = "durationMs";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#externalUrl
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, String> externalUrl;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#artifactType
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, String> artifactType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#startedAt
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, Instant> startedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#message
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, String> message;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#classification
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, String> classification;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#enabled
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, Boolean> enabled;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#finishedAt
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, Instant> finishedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#metricsJson
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, String> metricsJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#externalRunId
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, String> externalRunId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#artifactName
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, String> artifactName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#entryKey
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, String> entryKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#artifactId
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, UUID> artifactId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#id
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog
	 **/
	public static volatile EntityType<InfraExternalRunLog> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#durationMs
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, Long> durationMs;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#ownerDept
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalRunLog#status
	 **/
	public static volatile SingularAttribute<InfraExternalRunLog, String> status;

}

