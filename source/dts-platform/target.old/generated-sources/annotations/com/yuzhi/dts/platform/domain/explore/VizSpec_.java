package com.yuzhi.dts.platform.domain.explore;

import com.yuzhi.dts.platform.domain.explore.ExecEnums.VizType;
import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(VizSpec.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class VizSpec_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String ID = "id";
	public static final String TITLE = "title";
	public static final String TYPE = "type";
	public static final String CONFIG = "config";
	public static final String RESULT_SET_ID = "resultSetId";
	public static final String QUERY_ID = "queryId";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.VizSpec#id
	 **/
	public static volatile SingularAttribute<VizSpec, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.VizSpec#title
	 **/
	public static volatile SingularAttribute<VizSpec, String> title;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.VizSpec#type
	 **/
	public static volatile SingularAttribute<VizSpec, VizType> type;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.VizSpec
	 **/
	public static volatile EntityType<VizSpec> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.VizSpec#config
	 **/
	public static volatile SingularAttribute<VizSpec, String> config;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.VizSpec#resultSetId
	 **/
	public static volatile SingularAttribute<VizSpec, UUID> resultSetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.VizSpec#queryId
	 **/
	public static volatile SingularAttribute<VizSpec, UUID> queryId;

}

