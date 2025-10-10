package com.yuzhi.dts.platform.domain.service;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(InfraDataSource.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class InfraDataSource_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SECURE_KEY_VERSION = "secureKeyVersion";
	public static final String DESCRIPTION = "description";
	public static final String SECURE_PROPS = "secureProps";
	public static final String TYPE = "type";
	public static final String PROPS = "props";
	public static final String SECURE_IV = "secureIv";
	public static final String NAME = "name";
	public static final String JDBC_URL = "jdbcUrl";
	public static final String ID = "id";
	public static final String LAST_VERIFIED_AT = "lastVerifiedAt";
	public static final String USERNAME = "username";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataSource#secureKeyVersion
	 **/
	public static volatile SingularAttribute<InfraDataSource, String> secureKeyVersion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataSource#description
	 **/
	public static volatile SingularAttribute<InfraDataSource, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataSource#secureProps
	 **/
	public static volatile SingularAttribute<InfraDataSource, byte[]> secureProps;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataSource#type
	 **/
	public static volatile SingularAttribute<InfraDataSource, String> type;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataSource#props
	 **/
	public static volatile SingularAttribute<InfraDataSource, String> props;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataSource#secureIv
	 **/
	public static volatile SingularAttribute<InfraDataSource, byte[]> secureIv;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataSource#name
	 **/
	public static volatile SingularAttribute<InfraDataSource, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataSource#jdbcUrl
	 **/
	public static volatile SingularAttribute<InfraDataSource, String> jdbcUrl;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataSource#id
	 **/
	public static volatile SingularAttribute<InfraDataSource, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataSource#lastVerifiedAt
	 **/
	public static volatile SingularAttribute<InfraDataSource, Instant> lastVerifiedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataSource
	 **/
	public static volatile EntityType<InfraDataSource> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataSource#username
	 **/
	public static volatile SingularAttribute<InfraDataSource, String> username;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataSource#status
	 **/
	public static volatile SingularAttribute<InfraDataSource, String> status;

}

