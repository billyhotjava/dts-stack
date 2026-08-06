package com.yuzhi.dts.platform.domain.security;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(SecurityBackupPlan.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SecurityBackupPlan_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SCHEDULE = "schedule";
	public static final String LAST_RUN_AT = "lastRunAt";
	public static final String NOTES = "notes";
	public static final String RETENTION_DAYS = "retentionDays";
	public static final String LAST_RESULT = "lastResult";
	public static final String DESCRIPTION = "description";
	public static final String ID = "id";
	public static final String TARGET_KEY = "targetKey";
	public static final String TITLE = "title";
	public static final String ENABLED = "enabled";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupPlan#schedule
	 **/
	public static volatile SingularAttribute<SecurityBackupPlan, String> schedule;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupPlan#lastRunAt
	 **/
	public static volatile SingularAttribute<SecurityBackupPlan, Instant> lastRunAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupPlan#notes
	 **/
	public static volatile SingularAttribute<SecurityBackupPlan, String> notes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupPlan#retentionDays
	 **/
	public static volatile SingularAttribute<SecurityBackupPlan, Integer> retentionDays;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupPlan#lastResult
	 **/
	public static volatile SingularAttribute<SecurityBackupPlan, String> lastResult;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupPlan#description
	 **/
	public static volatile SingularAttribute<SecurityBackupPlan, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupPlan#id
	 **/
	public static volatile SingularAttribute<SecurityBackupPlan, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupPlan#targetKey
	 **/
	public static volatile SingularAttribute<SecurityBackupPlan, String> targetKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupPlan#title
	 **/
	public static volatile SingularAttribute<SecurityBackupPlan, String> title;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupPlan
	 **/
	public static volatile EntityType<SecurityBackupPlan> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBackupPlan#enabled
	 **/
	public static volatile SingularAttribute<SecurityBackupPlan, Boolean> enabled;

}

