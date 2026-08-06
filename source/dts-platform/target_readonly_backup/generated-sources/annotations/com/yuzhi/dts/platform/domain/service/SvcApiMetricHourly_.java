package com.yuzhi.dts.platform.domain.service;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(SvcApiMetricHourly.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SvcApiMetricHourly_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String CALL_COUNT = "callCount";
	public static final String BUCKET_START = "bucketStart";
	public static final String MASKED_HITS = "maskedHits";
	public static final String QPS_PEAK = "qpsPeak";
	public static final String ID = "id";
	public static final String DENY_COUNT = "denyCount";
	public static final String API_ID = "apiId";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApiMetricHourly#callCount
	 **/
	public static volatile SingularAttribute<SvcApiMetricHourly, Long> callCount;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApiMetricHourly#bucketStart
	 **/
	public static volatile SingularAttribute<SvcApiMetricHourly, Instant> bucketStart;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApiMetricHourly#maskedHits
	 **/
	public static volatile SingularAttribute<SvcApiMetricHourly, Integer> maskedHits;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApiMetricHourly#qpsPeak
	 **/
	public static volatile SingularAttribute<SvcApiMetricHourly, Integer> qpsPeak;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApiMetricHourly#id
	 **/
	public static volatile SingularAttribute<SvcApiMetricHourly, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApiMetricHourly
	 **/
	public static volatile EntityType<SvcApiMetricHourly> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApiMetricHourly#denyCount
	 **/
	public static volatile SingularAttribute<SvcApiMetricHourly, Integer> denyCount;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcApiMetricHourly#apiId
	 **/
	public static volatile SingularAttribute<SvcApiMetricHourly, UUID> apiId;

}

