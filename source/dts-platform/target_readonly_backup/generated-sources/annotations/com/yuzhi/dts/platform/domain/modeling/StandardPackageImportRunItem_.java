package com.yuzhi.dts.platform.domain.modeling;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(StandardPackageImportRunItem.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class StandardPackageImportRunItem_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String BEFORE_JSON = "beforeJson";
	public static final String ENTITY_TYPE = "entityType";
	public static final String ACTION = "action";
	public static final String ENTITY_ID = "entityId";
	public static final String ID = "id";
	public static final String RUN_ID = "runId";
	public static final String SEQ = "seq";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRunItem#beforeJson
	 **/
	public static volatile SingularAttribute<StandardPackageImportRunItem, String> beforeJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRunItem#entityType
	 **/
	public static volatile SingularAttribute<StandardPackageImportRunItem, String> entityType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRunItem#action
	 **/
	public static volatile SingularAttribute<StandardPackageImportRunItem, String> action;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRunItem#entityId
	 **/
	public static volatile SingularAttribute<StandardPackageImportRunItem, String> entityId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRunItem#id
	 **/
	public static volatile SingularAttribute<StandardPackageImportRunItem, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRunItem#runId
	 **/
	public static volatile SingularAttribute<StandardPackageImportRunItem, UUID> runId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRunItem
	 **/
	public static volatile EntityType<StandardPackageImportRunItem> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRunItem#seq
	 **/
	public static volatile SingularAttribute<StandardPackageImportRunItem, Integer> seq;

}

