package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(GovIndicatorTemplate.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovIndicatorTemplate_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String RECOMMENDED_SNAPSHOT = "recommendedSnapshot";
	public static final String CODE = "code";
	public static final String BUILTIN = "builtin";
	public static final String DISPLAY_ORDER = "displayOrder";
	public static final String DESCRIPTION = "description";
	public static final String SEED_TABLES = "seedTables";
	public static final String INDICATOR_BLUEPRINTS = "indicatorBlueprints";
	public static final String ENABLED = "enabled";
	public static final String DOMAIN = "domain";
	public static final String NAME = "name";
	public static final String REQUIRED_SOURCE_FIELDS = "requiredSourceFields";
	public static final String ID = "id";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate#recommendedSnapshot
	 **/
	public static volatile SingularAttribute<GovIndicatorTemplate, Boolean> recommendedSnapshot;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate#code
	 **/
	public static volatile SingularAttribute<GovIndicatorTemplate, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate#builtin
	 **/
	public static volatile SingularAttribute<GovIndicatorTemplate, Boolean> builtin;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate#displayOrder
	 **/
	public static volatile SingularAttribute<GovIndicatorTemplate, Integer> displayOrder;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate#description
	 **/
	public static volatile SingularAttribute<GovIndicatorTemplate, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate#seedTables
	 **/
	public static volatile SingularAttribute<GovIndicatorTemplate, String> seedTables;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate#indicatorBlueprints
	 **/
	public static volatile SingularAttribute<GovIndicatorTemplate, String> indicatorBlueprints;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate#enabled
	 **/
	public static volatile SingularAttribute<GovIndicatorTemplate, Boolean> enabled;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate#domain
	 **/
	public static volatile SingularAttribute<GovIndicatorTemplate, String> domain;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate#name
	 **/
	public static volatile SingularAttribute<GovIndicatorTemplate, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate#requiredSourceFields
	 **/
	public static volatile SingularAttribute<GovIndicatorTemplate, String> requiredSourceFields;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate#id
	 **/
	public static volatile SingularAttribute<GovIndicatorTemplate, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorTemplate
	 **/
	public static volatile EntityType<GovIndicatorTemplate> class_;

}

