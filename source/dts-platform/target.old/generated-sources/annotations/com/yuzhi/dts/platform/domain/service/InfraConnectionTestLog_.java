package com.yuzhi.dts.platform.domain.service;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(InfraConnectionTestLog.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class InfraConnectionTestLog_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String RESULT = "result";
	public static final String DATA_SOURCE_ID = "dataSourceId";
	public static final String REQUEST_PAYLOAD = "requestPayload";
	public static final String ID = "id";
	public static final String MESSAGE = "message";
	public static final String ELAPSED_MS = "elapsedMs";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraConnectionTestLog#result
	 **/
	public static volatile SingularAttribute<InfraConnectionTestLog, String> result;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraConnectionTestLog#dataSourceId
	 **/
	public static volatile SingularAttribute<InfraConnectionTestLog, UUID> dataSourceId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraConnectionTestLog#requestPayload
	 **/
	public static volatile SingularAttribute<InfraConnectionTestLog, String> requestPayload;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraConnectionTestLog#id
	 **/
	public static volatile SingularAttribute<InfraConnectionTestLog, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraConnectionTestLog#message
	 **/
	public static volatile SingularAttribute<InfraConnectionTestLog, String> message;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraConnectionTestLog
	 **/
	public static volatile EntityType<InfraConnectionTestLog> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraConnectionTestLog#elapsedMs
	 **/
	public static volatile SingularAttribute<InfraConnectionTestLog, Integer> elapsedMs;

}

