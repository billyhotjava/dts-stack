package com.yuzhi.dts.platform.domain.explore;

import com.yuzhi.dts.platform.domain.explore.ExecEnums.ExecEngine;
import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(SqlConnection.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SqlConnection_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String ENGINE = "engine";
	public static final String CATALOG = "catalog";
	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String SCHEMA_NAME = "schemaName";
	public static final String PROPS = "props";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SqlConnection#engine
	 **/
	public static volatile SingularAttribute<SqlConnection, ExecEngine> engine;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SqlConnection#catalog
	 **/
	public static volatile SingularAttribute<SqlConnection, String> catalog;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SqlConnection#name
	 **/
	public static volatile SingularAttribute<SqlConnection, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SqlConnection#id
	 **/
	public static volatile SingularAttribute<SqlConnection, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SqlConnection#schemaName
	 **/
	public static volatile SingularAttribute<SqlConnection, String> schemaName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SqlConnection
	 **/
	public static volatile EntityType<SqlConnection> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SqlConnection#props
	 **/
	public static volatile SingularAttribute<SqlConnection, String> props;

}

