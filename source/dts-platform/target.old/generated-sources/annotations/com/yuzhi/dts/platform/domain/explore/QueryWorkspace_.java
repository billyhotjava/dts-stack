package com.yuzhi.dts.platform.domain.explore;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(QueryWorkspace.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class QueryWorkspace_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String VARIABLES = "variables";
	public static final String DEFAULT_CONNECTION = "defaultConnection";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String ID = "id";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryWorkspace#variables
	 **/
	public static volatile SingularAttribute<QueryWorkspace, String> variables;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryWorkspace#defaultConnection
	 **/
	public static volatile SingularAttribute<QueryWorkspace, String> defaultConnection;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryWorkspace#name
	 **/
	public static volatile SingularAttribute<QueryWorkspace, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryWorkspace#description
	 **/
	public static volatile SingularAttribute<QueryWorkspace, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryWorkspace#id
	 **/
	public static volatile SingularAttribute<QueryWorkspace, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryWorkspace
	 **/
	public static volatile EntityType<QueryWorkspace> class_;

}

