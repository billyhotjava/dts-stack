package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@StaticMetamodel(GovQualityTemplate.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovQualityTemplate_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String CODE = "code";
	public static final String DIALECT = "dialect";
	public static final String SEVERITY_DEFAULT = "severityDefault";
	public static final String BUILTIN = "builtin";
	public static final String DESCRIPTION = "description";
	public static final String ENABLED = "enabled";
	public static final String SQL_TEMPLATE = "sqlTemplate";
	public static final String PARAM_SCHEMA = "paramSchema";
	public static final String NAME = "name";
	public static final String ACTION_DEFAULT = "actionDefault";
	public static final String ID = "id";
	public static final String CATEGORY = "category";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTemplate#code
	 **/
	public static volatile SingularAttribute<GovQualityTemplate, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTemplate#dialect
	 **/
	public static volatile SingularAttribute<GovQualityTemplate, String> dialect;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTemplate#severityDefault
	 **/
	public static volatile SingularAttribute<GovQualityTemplate, String> severityDefault;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTemplate#builtin
	 **/
	public static volatile SingularAttribute<GovQualityTemplate, Boolean> builtin;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTemplate#description
	 **/
	public static volatile SingularAttribute<GovQualityTemplate, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTemplate#enabled
	 **/
	public static volatile SingularAttribute<GovQualityTemplate, Boolean> enabled;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTemplate#sqlTemplate
	 **/
	public static volatile SingularAttribute<GovQualityTemplate, String> sqlTemplate;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTemplate#paramSchema
	 **/
	public static volatile SingularAttribute<GovQualityTemplate, List<Map<String,Object>>> paramSchema;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTemplate#name
	 **/
	public static volatile SingularAttribute<GovQualityTemplate, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTemplate#actionDefault
	 **/
	public static volatile SingularAttribute<GovQualityTemplate, String> actionDefault;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTemplate#id
	 **/
	public static volatile SingularAttribute<GovQualityTemplate, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTemplate#category
	 **/
	public static volatile SingularAttribute<GovQualityTemplate, String> category;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityTemplate
	 **/
	public static volatile EntityType<GovQualityTemplate> class_;

}

