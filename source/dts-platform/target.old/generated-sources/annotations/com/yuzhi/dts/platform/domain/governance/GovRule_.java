package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(GovRule.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovRule_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String OWNER = "owner";
	public static final String SEVERITY = "severity";
	public static final String TEMPLATE = "template";
	public static final String CODE = "code";
	public static final String EXPRESSION = "expression";
	public static final String DESCRIPTION = "description";
	public static final String TYPE = "type";
	public static final String ENABLED = "enabled";
	public static final String FREQUENCY_CRON = "frequencyCron";
	public static final String LATEST_VERSION = "latestVersion";
	public static final String VERSIONS = "versions";
	public static final String EXECUTOR = "executor";
	public static final String NAME = "name";
	public static final String DATASET_ID = "datasetId";
	public static final String ID = "id";
	public static final String CATEGORY = "category";
	public static final String DATA_LEVEL = "dataLevel";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#owner
	 **/
	public static volatile SingularAttribute<GovRule, String> owner;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#severity
	 **/
	public static volatile SingularAttribute<GovRule, String> severity;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#template
	 **/
	public static volatile SingularAttribute<GovRule, Boolean> template;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#code
	 **/
	public static volatile SingularAttribute<GovRule, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#expression
	 **/
	public static volatile SingularAttribute<GovRule, String> expression;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#description
	 **/
	public static volatile SingularAttribute<GovRule, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#type
	 **/
	public static volatile SingularAttribute<GovRule, String> type;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#enabled
	 **/
	public static volatile SingularAttribute<GovRule, Boolean> enabled;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#frequencyCron
	 **/
	public static volatile SingularAttribute<GovRule, String> frequencyCron;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#latestVersion
	 **/
	public static volatile SingularAttribute<GovRule, GovRuleVersion> latestVersion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#versions
	 **/
	public static volatile SetAttribute<GovRule, GovRuleVersion> versions;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#executor
	 **/
	public static volatile SingularAttribute<GovRule, String> executor;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#name
	 **/
	public static volatile SingularAttribute<GovRule, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#datasetId
	 **/
	public static volatile SingularAttribute<GovRule, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#id
	 **/
	public static volatile SingularAttribute<GovRule, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#category
	 **/
	public static volatile SingularAttribute<GovRule, String> category;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule
	 **/
	public static volatile EntityType<GovRule> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRule#dataLevel
	 **/
	public static volatile SingularAttribute<GovRule, String> dataLevel;

}

