package com.yuzhi.dts.platform.domain.modeling;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(DataStandardVersion.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class DataStandardVersion_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String STANDARD = "standard";
	public static final String RELEASED_AT = "releasedAt";
	public static final String SNAPSHOT_JSON = "snapshotJson";
	public static final String ID = "id";
	public static final String VERSION = "version";
	public static final String CHANGE_SUMMARY = "changeSummary";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardVersion#standard
	 **/
	public static volatile SingularAttribute<DataStandardVersion, DataStandard> standard;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardVersion#releasedAt
	 **/
	public static volatile SingularAttribute<DataStandardVersion, Instant> releasedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardVersion#snapshotJson
	 **/
	public static volatile SingularAttribute<DataStandardVersion, String> snapshotJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardVersion#id
	 **/
	public static volatile SingularAttribute<DataStandardVersion, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardVersion
	 **/
	public static volatile EntityType<DataStandardVersion> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardVersion#version
	 **/
	public static volatile SingularAttribute<DataStandardVersion, String> version;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardVersion#changeSummary
	 **/
	public static volatile SingularAttribute<DataStandardVersion, String> changeSummary;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardVersion#status
	 **/
	public static volatile SingularAttribute<DataStandardVersion, DataStandardVersionStatus> status;

}

