package com.yuzhi.dts.platform.domain.service;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(InfraTaskSchedule.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class InfraTaskSchedule_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String CRON = "cron";
	public static final String LAST_RUN_AT = "lastRunAt";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String ID = "id";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraTaskSchedule#cron
	 **/
	public static volatile SingularAttribute<InfraTaskSchedule, String> cron;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraTaskSchedule#lastRunAt
	 **/
	public static volatile SingularAttribute<InfraTaskSchedule, Instant> lastRunAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraTaskSchedule#name
	 **/
	public static volatile SingularAttribute<InfraTaskSchedule, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraTaskSchedule#description
	 **/
	public static volatile SingularAttribute<InfraTaskSchedule, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraTaskSchedule#id
	 **/
	public static volatile SingularAttribute<InfraTaskSchedule, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraTaskSchedule
	 **/
	public static volatile EntityType<InfraTaskSchedule> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraTaskSchedule#status
	 **/
	public static volatile SingularAttribute<InfraTaskSchedule, String> status;

}

