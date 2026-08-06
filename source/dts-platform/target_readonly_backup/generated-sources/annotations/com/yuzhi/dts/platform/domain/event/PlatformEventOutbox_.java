package com.yuzhi.dts.platform.domain.event;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(PlatformEventOutbox.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class PlatformEventOutbox_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SEVERITY = "severity";
	public static final String TRACE_ID = "traceId";
	public static final String EVENT_ID = "eventId";
	public static final String PAYLOAD_JSON = "payloadJson";
	public static final String OCCURRED_AT = "occurredAt";
	public static final String SOURCE_APP = "sourceApp";
	public static final String AUDIT_ACTION_CODE = "auditActionCode";
	public static final String DISPATCH_ATTEMPTS = "dispatchAttempts";
	public static final String EVENT_TYPE = "eventType";
	public static final String DISPATCH_ERROR = "dispatchError";
	public static final String POLICY_REF = "policyRef";
	public static final String DISPATCH_STATUS = "dispatchStatus";
	public static final String AGGREGATE_TYPE = "aggregateType";
	public static final String ACTOR = "actor";
	public static final String DISPATCHED_AT = "dispatchedAt";
	public static final String AGGREGATE_ID = "aggregateId";
	public static final String DOMAIN = "domain";
	public static final String AGGREGATE_NAME = "aggregateName";
	public static final String ACTION = "action";
	public static final String CORRELATION_ID = "correlationId";
	public static final String ID = "id";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#severity
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> severity;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#traceId
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> traceId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#eventId
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> eventId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#payloadJson
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> payloadJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#occurredAt
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, Instant> occurredAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#sourceApp
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> sourceApp;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#auditActionCode
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> auditActionCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#dispatchAttempts
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, Integer> dispatchAttempts;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#eventType
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> eventType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#dispatchError
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> dispatchError;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#policyRef
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> policyRef;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#dispatchStatus
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> dispatchStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#aggregateType
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> aggregateType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#actor
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> actor;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#dispatchedAt
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, Instant> dispatchedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#aggregateId
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> aggregateId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#domain
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> domain;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#aggregateName
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> aggregateName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#action
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> action;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#correlationId
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> correlationId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#id
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox
	 **/
	public static volatile EntityType<PlatformEventOutbox> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.event.PlatformEventOutbox#status
	 **/
	public static volatile SingularAttribute<PlatformEventOutbox, String> status;

}

