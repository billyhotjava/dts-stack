package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(StdCodeMapping.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class StdCodeMapping_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String CODE_TYPE_ID = "codeTypeId";
	public static final String STD_CODE = "stdCode";
	public static final String SOURCE_SYS = "sourceSys";
	public static final String SRC_CODE = "srcCode";
	public static final String MAP_ID = "mapId";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeMapping#codeTypeId
	 **/
	public static volatile SingularAttribute<StdCodeMapping, String> codeTypeId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeMapping#stdCode
	 **/
	public static volatile SingularAttribute<StdCodeMapping, String> stdCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeMapping#sourceSys
	 **/
	public static volatile SingularAttribute<StdCodeMapping, String> sourceSys;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeMapping#srcCode
	 **/
	public static volatile SingularAttribute<StdCodeMapping, String> srcCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeMapping#mapId
	 **/
	public static volatile SingularAttribute<StdCodeMapping, Long> mapId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeMapping
	 **/
	public static volatile EntityType<StdCodeMapping> class_;

}

