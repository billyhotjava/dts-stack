package com.yuzhi.dts.platform.domain.ops;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@StaticMetamodel(OpsBackfillRequest.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class OpsBackfillRequest_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String PAYLOAD_JSON = "payloadJson";
	public static final String EXTERNAL_RUN_ID = "externalRunId";
	public static final String NAME = "name";
	public static final String DATE_TO = "dateTo";
	public static final String ID = "id";
	public static final String DAG_ID = "dagId";
	public static final String TRIGGERED_AT = "triggeredAt";
	public static final String DATE_FROM = "dateFrom";
	public static final String MESSAGE = "message";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest#payloadJson
	 **/
	public static volatile SingularAttribute<OpsBackfillRequest, String> payloadJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest#externalRunId
	 **/
	public static volatile SingularAttribute<OpsBackfillRequest, String> externalRunId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest#name
	 **/
	public static volatile SingularAttribute<OpsBackfillRequest, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest#dateTo
	 **/
	public static volatile SingularAttribute<OpsBackfillRequest, LocalDate> dateTo;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest#id
	 **/
	public static volatile SingularAttribute<OpsBackfillRequest, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest#dagId
	 **/
	public static volatile SingularAttribute<OpsBackfillRequest, String> dagId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest#triggeredAt
	 **/
	public static volatile SingularAttribute<OpsBackfillRequest, Instant> triggeredAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest#dateFrom
	 **/
	public static volatile SingularAttribute<OpsBackfillRequest, LocalDate> dateFrom;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest#message
	 **/
	public static volatile SingularAttribute<OpsBackfillRequest, String> message;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest
	 **/
	public static volatile EntityType<OpsBackfillRequest> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.ops.OpsBackfillRequest#status
	 **/
	public static volatile SingularAttribute<OpsBackfillRequest, String> status;

}

