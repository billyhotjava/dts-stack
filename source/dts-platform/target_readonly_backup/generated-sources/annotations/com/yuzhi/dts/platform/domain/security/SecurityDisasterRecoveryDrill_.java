package com.yuzhi.dts.platform.domain.security;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.LocalDate;
import java.util.UUID;

@StaticMetamodel(SecurityDisasterRecoveryDrill.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SecurityDisasterRecoveryDrill_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String RESULT = "result";
	public static final String SUMMARY = "summary";
	public static final String DRILL_DATE = "drillDate";
	public static final String SCENARIO = "scenario";
	public static final String RPO_MINUTES = "rpoMinutes";
	public static final String RTO_MINUTES = "rtoMinutes";
	public static final String ID = "id";
	public static final String TARGET_KEY = "targetKey";
	public static final String EVIDENCE_URI = "evidenceUri";
	public static final String DETAILS_JSON = "detailsJson";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityDisasterRecoveryDrill#result
	 **/
	public static volatile SingularAttribute<SecurityDisasterRecoveryDrill, String> result;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityDisasterRecoveryDrill#summary
	 **/
	public static volatile SingularAttribute<SecurityDisasterRecoveryDrill, String> summary;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityDisasterRecoveryDrill#drillDate
	 **/
	public static volatile SingularAttribute<SecurityDisasterRecoveryDrill, LocalDate> drillDate;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityDisasterRecoveryDrill#scenario
	 **/
	public static volatile SingularAttribute<SecurityDisasterRecoveryDrill, String> scenario;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityDisasterRecoveryDrill#rpoMinutes
	 **/
	public static volatile SingularAttribute<SecurityDisasterRecoveryDrill, Integer> rpoMinutes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityDisasterRecoveryDrill#rtoMinutes
	 **/
	public static volatile SingularAttribute<SecurityDisasterRecoveryDrill, Integer> rtoMinutes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityDisasterRecoveryDrill#id
	 **/
	public static volatile SingularAttribute<SecurityDisasterRecoveryDrill, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityDisasterRecoveryDrill#targetKey
	 **/
	public static volatile SingularAttribute<SecurityDisasterRecoveryDrill, String> targetKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityDisasterRecoveryDrill
	 **/
	public static volatile EntityType<SecurityDisasterRecoveryDrill> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityDisasterRecoveryDrill#evidenceUri
	 **/
	public static volatile SingularAttribute<SecurityDisasterRecoveryDrill, String> evidenceUri;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityDisasterRecoveryDrill#detailsJson
	 **/
	public static volatile SingularAttribute<SecurityDisasterRecoveryDrill, String> detailsJson;

}

