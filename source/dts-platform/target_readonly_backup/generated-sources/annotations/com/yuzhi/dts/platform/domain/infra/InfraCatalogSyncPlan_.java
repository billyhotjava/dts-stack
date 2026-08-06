package com.yuzhi.dts.platform.domain.infra;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(InfraCatalogSyncPlan.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class InfraCatalogSyncPlan_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SOURCE_ID = "sourceId";
	public static final String OWNER = "owner";
	public static final String CRON = "cron";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String ID = "id";
	public static final String DAG_ID = "dagId";
	public static final String ENABLED = "enabled";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncPlan#sourceId
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncPlan, UUID> sourceId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncPlan#owner
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncPlan, String> owner;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncPlan#cron
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncPlan, String> cron;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncPlan#name
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncPlan, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncPlan#description
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncPlan, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncPlan#id
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncPlan, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncPlan#dagId
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncPlan, String> dagId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncPlan
	 **/
	public static volatile EntityType<InfraCatalogSyncPlan> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraCatalogSyncPlan#enabled
	 **/
	public static volatile SingularAttribute<InfraCatalogSyncPlan, Boolean> enabled;

}

