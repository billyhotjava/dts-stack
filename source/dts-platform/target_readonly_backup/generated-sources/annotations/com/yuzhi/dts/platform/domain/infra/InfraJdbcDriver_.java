package com.yuzhi.dts.platform.domain.infra;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(InfraJdbcDriver.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class InfraJdbcDriver_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String FILE_NAME = "fileName";
	public static final String DRIVER_CLASS = "driverClass";
	public static final String FILE_PATH = "filePath";
	public static final String JDK_SPEC = "jdkSpec";
	public static final String ID = "id";
	public static final String VERSION = "version";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraJdbcDriver#fileName
	 **/
	public static volatile SingularAttribute<InfraJdbcDriver, String> fileName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraJdbcDriver#driverClass
	 **/
	public static volatile SingularAttribute<InfraJdbcDriver, String> driverClass;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraJdbcDriver#filePath
	 **/
	public static volatile SingularAttribute<InfraJdbcDriver, String> filePath;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraJdbcDriver#jdkSpec
	 **/
	public static volatile SingularAttribute<InfraJdbcDriver, String> jdkSpec;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraJdbcDriver#id
	 **/
	public static volatile SingularAttribute<InfraJdbcDriver, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraJdbcDriver
	 **/
	public static volatile EntityType<InfraJdbcDriver> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraJdbcDriver#version
	 **/
	public static volatile SingularAttribute<InfraJdbcDriver, String> version;

}

