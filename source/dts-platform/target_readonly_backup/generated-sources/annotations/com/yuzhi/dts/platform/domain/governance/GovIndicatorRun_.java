package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(GovIndicatorRun.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovIndicatorRun_ {

	public static final String COMPUTED_VALUE = "computedValue";
	public static final String RUN_AT = "runAt";
	public static final String ALERT_REASON = "alertReason";
	public static final String ROWS_PROCESSED = "rowsProcessed";
	public static final String ALERT_LEVEL = "alertLevel";
	public static final String ERROR_MESSAGE = "errorMessage";
	public static final String PREVIOUS_VALUE = "previousValue";
	public static final String INDICATOR_ID = "indicatorId";
	public static final String DBT_RUN_ID = "dbtRunId";
	public static final String ID = "id";
	public static final String CHANGE_RATE = "changeRate";
	public static final String DURATION_MS = "durationMs";
	public static final String THRESHOLD_HIT = "thresholdHit";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun#computedValue
	 **/
	public static volatile SingularAttribute<GovIndicatorRun, BigDecimal> computedValue;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun#runAt
	 **/
	public static volatile SingularAttribute<GovIndicatorRun, Instant> runAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun#alertReason
	 **/
	public static volatile SingularAttribute<GovIndicatorRun, String> alertReason;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun#rowsProcessed
	 **/
	public static volatile SingularAttribute<GovIndicatorRun, Integer> rowsProcessed;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun#alertLevel
	 **/
	public static volatile SingularAttribute<GovIndicatorRun, String> alertLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun#errorMessage
	 **/
	public static volatile SingularAttribute<GovIndicatorRun, String> errorMessage;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun#previousValue
	 **/
	public static volatile SingularAttribute<GovIndicatorRun, BigDecimal> previousValue;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun#indicatorId
	 **/
	public static volatile SingularAttribute<GovIndicatorRun, UUID> indicatorId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun#dbtRunId
	 **/
	public static volatile SingularAttribute<GovIndicatorRun, String> dbtRunId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun#id
	 **/
	public static volatile SingularAttribute<GovIndicatorRun, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun#changeRate
	 **/
	public static volatile SingularAttribute<GovIndicatorRun, BigDecimal> changeRate;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun
	 **/
	public static volatile EntityType<GovIndicatorRun> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun#durationMs
	 **/
	public static volatile SingularAttribute<GovIndicatorRun, Integer> durationMs;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun#thresholdHit
	 **/
	public static volatile SingularAttribute<GovIndicatorRun, Boolean> thresholdHit;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorRun#status
	 **/
	public static volatile SingularAttribute<GovIndicatorRun, String> status;

}

