package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.math.BigDecimal;
import java.util.UUID;

@StaticMetamodel(GovQualityMetric.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovQualityMetric_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String METRIC_KEY = "metricKey";
	public static final String METRIC_VALUE = "metricValue";
	public static final String RUN = "run";
	public static final String THRESHOLD_VALUE = "thresholdValue";
	public static final String ID = "id";
	public static final String DETAIL = "detail";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityMetric#metricKey
	 **/
	public static volatile SingularAttribute<GovQualityMetric, String> metricKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityMetric#metricValue
	 **/
	public static volatile SingularAttribute<GovQualityMetric, BigDecimal> metricValue;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityMetric#run
	 **/
	public static volatile SingularAttribute<GovQualityMetric, GovQualityRun> run;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityMetric#thresholdValue
	 **/
	public static volatile SingularAttribute<GovQualityMetric, BigDecimal> thresholdValue;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityMetric#id
	 **/
	public static volatile SingularAttribute<GovQualityMetric, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityMetric#detail
	 **/
	public static volatile SingularAttribute<GovQualityMetric, String> detail;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityMetric
	 **/
	public static volatile EntityType<GovQualityMetric> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityMetric#status
	 **/
	public static volatile SingularAttribute<GovQualityMetric, String> status;

}

