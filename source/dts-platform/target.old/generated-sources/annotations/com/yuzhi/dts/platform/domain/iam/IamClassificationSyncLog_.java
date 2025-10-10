package com.yuzhi.dts.platform.domain.iam;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(IamClassificationSyncLog.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class IamClassificationSyncLog_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String FAILURE_JSON = "failureJson";
	public static final String STARTED_AT = "startedAt";
	public static final String ID = "id";
	public static final String DELTA_COUNT = "deltaCount";
	public static final String STATUS = "status";
	public static final String FINISHED_AT = "finishedAt";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamClassificationSyncLog#failureJson
	 **/
	public static volatile SingularAttribute<IamClassificationSyncLog, String> failureJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamClassificationSyncLog#startedAt
	 **/
	public static volatile SingularAttribute<IamClassificationSyncLog, Instant> startedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamClassificationSyncLog#id
	 **/
	public static volatile SingularAttribute<IamClassificationSyncLog, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamClassificationSyncLog#deltaCount
	 **/
	public static volatile SingularAttribute<IamClassificationSyncLog, Integer> deltaCount;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamClassificationSyncLog
	 **/
	public static volatile EntityType<IamClassificationSyncLog> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamClassificationSyncLog#status
	 **/
	public static volatile SingularAttribute<IamClassificationSyncLog, String> status;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamClassificationSyncLog#finishedAt
	 **/
	public static volatile SingularAttribute<IamClassificationSyncLog, Instant> finishedAt;

}

