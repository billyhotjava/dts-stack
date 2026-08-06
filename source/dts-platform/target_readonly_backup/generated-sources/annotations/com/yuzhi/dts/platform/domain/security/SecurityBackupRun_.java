package com.yuzhi.dts.platform.domain.security;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(SecurityBackupRun.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SecurityBackupRun_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String RESULT = "result";
	public static final String SUMMARY = "summary";
	public static final String STARTED_AT = "startedAt";
	public static final String ID = "id";
	public static final String PLAN = "plan";
	public static final String ARTIFACT_URI = "artifactUri";
	public static final String FINISHED_AT = "finishedAt";
	public static final String DETAILS_JSON = "detailsJson";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupRun#result
	 **/
	public static volatile SingularAttribute<SecurityBackupRun, String> result;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupRun#summary
	 **/
	public static volatile SingularAttribute<SecurityBackupRun, String> summary;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupRun#startedAt
	 **/
	public static volatile SingularAttribute<SecurityBackupRun, Instant> startedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupRun#id
	 **/
	public static volatile SingularAttribute<SecurityBackupRun, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupRun
	 **/
	public static volatile EntityType<SecurityBackupRun> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupRun#plan
	 **/
	public static volatile SingularAttribute<SecurityBackupRun, SecurityBackupPlan> plan;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupRun#artifactUri
	 **/
	public static volatile SingularAttribute<SecurityBackupRun, String> artifactUri;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupRun#finishedAt
	 **/
	public static volatile SingularAttribute<SecurityBackupRun, Instant> finishedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupRun#detailsJson
	 **/
	public static volatile SingularAttribute<SecurityBackupRun, String> detailsJson;

}

