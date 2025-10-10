package com.yuzhi.dts.platform.domain.iam;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(IamUserClassification.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class IamUserClassification_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SECURITY_LEVEL = "securityLevel";
	public static final String PROJECTS = "projects";
	public static final String ORG_PATH = "orgPath";
	public static final String DISPLAY_NAME = "displayName";
	public static final String ROLES = "roles";
	public static final String ID = "id";
	public static final String SYNCED_AT = "syncedAt";
	public static final String USERNAME = "username";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamUserClassification#securityLevel
	 **/
	public static volatile SingularAttribute<IamUserClassification, String> securityLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamUserClassification#projects
	 **/
	public static volatile SingularAttribute<IamUserClassification, String> projects;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamUserClassification#orgPath
	 **/
	public static volatile SingularAttribute<IamUserClassification, String> orgPath;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamUserClassification#displayName
	 **/
	public static volatile SingularAttribute<IamUserClassification, String> displayName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamUserClassification#roles
	 **/
	public static volatile SingularAttribute<IamUserClassification, String> roles;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamUserClassification#id
	 **/
	public static volatile SingularAttribute<IamUserClassification, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamUserClassification
	 **/
	public static volatile EntityType<IamUserClassification> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamUserClassification#syncedAt
	 **/
	public static volatile SingularAttribute<IamUserClassification, Instant> syncedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamUserClassification#username
	 **/
	public static volatile SingularAttribute<IamUserClassification, String> username;

}

