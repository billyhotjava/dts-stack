package com.yuzhi.dts.platform.domain.modeling;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(DataStandard.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class DataStandard_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String OWNER = "owner";
	public static final String CODE = "code";
	public static final String LAST_REVIEW_AT = "lastReviewAt";
	public static final String REVIEW_CYCLE = "reviewCycle";
	public static final String DESCRIPTION = "description";
	public static final String CURRENT_VERSION = "currentVersion";
	public static final String TAGS = "tags";
	public static final String SECURITY_LEVEL = "securityLevel";
	public static final String DOMAIN = "domain";
	public static final String SCOPE = "scope";
	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String VERSION_NOTES = "versionNotes";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard#owner
	 **/
	public static volatile SingularAttribute<DataStandard, String> owner;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard#code
	 **/
	public static volatile SingularAttribute<DataStandard, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard#lastReviewAt
	 **/
	public static volatile SingularAttribute<DataStandard, Instant> lastReviewAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard#reviewCycle
	 **/
	public static volatile SingularAttribute<DataStandard, String> reviewCycle;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard#description
	 **/
	public static volatile SingularAttribute<DataStandard, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard#currentVersion
	 **/
	public static volatile SingularAttribute<DataStandard, String> currentVersion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard#tags
	 **/
	public static volatile SingularAttribute<DataStandard, String> tags;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard#securityLevel
	 **/
	public static volatile SingularAttribute<DataStandard, DataSecurityLevel> securityLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard#domain
	 **/
	public static volatile SingularAttribute<DataStandard, String> domain;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard#scope
	 **/
	public static volatile SingularAttribute<DataStandard, String> scope;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard#name
	 **/
	public static volatile SingularAttribute<DataStandard, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard#id
	 **/
	public static volatile SingularAttribute<DataStandard, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard#versionNotes
	 **/
	public static volatile SingularAttribute<DataStandard, String> versionNotes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard
	 **/
	public static volatile EntityType<DataStandard> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandard#status
	 **/
	public static volatile SingularAttribute<DataStandard, DataStandardStatus> status;

}

