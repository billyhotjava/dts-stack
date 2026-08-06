package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(GovQualityTask.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovQualityTask_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String NAME = "name";
	public static final String DATASET_ID = "datasetId";
	public static final String INTERVAL_MINUTES = "intervalMinutes";
	public static final String ID = "id";
	public static final String LAST_TRIGGERED_AT = "lastTriggeredAt";
	public static final String RULE_ID = "ruleId";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String ENABLED = "enabled";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTask#name
	 **/
	public static volatile SingularAttribute<GovQualityTask, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTask#datasetId
	 **/
	public static volatile SingularAttribute<GovQualityTask, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTask#intervalMinutes
	 **/
	public static volatile SingularAttribute<GovQualityTask, Integer> intervalMinutes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTask#id
	 **/
	public static volatile SingularAttribute<GovQualityTask, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTask#lastTriggeredAt
	 **/
	public static volatile SingularAttribute<GovQualityTask, Instant> lastTriggeredAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTask#ruleId
	 **/
	public static volatile SingularAttribute<GovQualityTask, UUID> ruleId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTask
	 **/
	public static volatile EntityType<GovQualityTask> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTask#ownerDept
	 **/
	public static volatile SingularAttribute<GovQualityTask, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTask#enabled
	 **/
	public static volatile SingularAttribute<GovQualityTask, Boolean> enabled;

}

