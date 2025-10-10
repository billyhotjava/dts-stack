package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(GovRuleBinding.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovRuleBinding_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SCHEDULE_OVERRIDE = "scheduleOverride";
	public static final String FIELD_REFS = "fieldRefs";
	public static final String SCOPE_TYPE = "scopeType";
	public static final String RULE_VERSION = "ruleVersion";
	public static final String FILTER_EXPRESSION = "filterExpression";
	public static final String DATASET_ID = "datasetId";
	public static final String ID = "id";
	public static final String DATASET_ALIAS = "datasetAlias";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleBinding#scheduleOverride
	 **/
	public static volatile SingularAttribute<GovRuleBinding, String> scheduleOverride;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleBinding#fieldRefs
	 **/
	public static volatile SingularAttribute<GovRuleBinding, String> fieldRefs;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleBinding#scopeType
	 **/
	public static volatile SingularAttribute<GovRuleBinding, String> scopeType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleBinding#ruleVersion
	 **/
	public static volatile SingularAttribute<GovRuleBinding, GovRuleVersion> ruleVersion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleBinding#filterExpression
	 **/
	public static volatile SingularAttribute<GovRuleBinding, String> filterExpression;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleBinding#datasetId
	 **/
	public static volatile SingularAttribute<GovRuleBinding, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleBinding#id
	 **/
	public static volatile SingularAttribute<GovRuleBinding, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleBinding
	 **/
	public static volatile EntityType<GovRuleBinding> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleBinding#datasetAlias
	 **/
	public static volatile SingularAttribute<GovRuleBinding, String> datasetAlias;

}

