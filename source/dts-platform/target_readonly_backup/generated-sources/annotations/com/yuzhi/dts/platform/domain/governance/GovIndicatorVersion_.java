package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(GovIndicatorVersion.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovIndicatorVersion_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String INDICATOR = "indicator";
	public static final String RELEASED_AT = "releasedAt";
	public static final String SNAPSHOT_JSON = "snapshotJson";
	public static final String ID = "id";
	public static final String VERSION = "version";
	public static final String CHANGE_SUMMARY = "changeSummary";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion#indicator
	 **/
	public static volatile SingularAttribute<GovIndicatorVersion, GovIndicatorDefinition> indicator;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion#releasedAt
	 **/
	public static volatile SingularAttribute<GovIndicatorVersion, Instant> releasedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion#snapshotJson
	 **/
	public static volatile SingularAttribute<GovIndicatorVersion, String> snapshotJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion#id
	 **/
	public static volatile SingularAttribute<GovIndicatorVersion, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion
	 **/
	public static volatile EntityType<GovIndicatorVersion> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion#version
	 **/
	public static volatile SingularAttribute<GovIndicatorVersion, String> version;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion#changeSummary
	 **/
	public static volatile SingularAttribute<GovIndicatorVersion, String> changeSummary;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion#status
	 **/
	public static volatile SingularAttribute<GovIndicatorVersion, String> status;

}

