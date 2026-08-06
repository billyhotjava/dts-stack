package com.yuzhi.dts.platform.domain.modeling;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(ModelingTemplateVersion.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class ModelingTemplateVersion_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String TEMPLATE = "template";
	public static final String RELEASED_AT = "releasedAt";
	public static final String SNAPSHOT_JSON = "snapshotJson";
	public static final String ID = "id";
	public static final String VERSION = "version";
	public static final String CHANGE_SUMMARY = "changeSummary";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplateVersion#template
	 **/
	public static volatile SingularAttribute<ModelingTemplateVersion, ModelingTemplate> template;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplateVersion#releasedAt
	 **/
	public static volatile SingularAttribute<ModelingTemplateVersion, Instant> releasedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplateVersion#snapshotJson
	 **/
	public static volatile SingularAttribute<ModelingTemplateVersion, String> snapshotJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplateVersion#id
	 **/
	public static volatile SingularAttribute<ModelingTemplateVersion, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplateVersion
	 **/
	public static volatile EntityType<ModelingTemplateVersion> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplateVersion#version
	 **/
	public static volatile SingularAttribute<ModelingTemplateVersion, String> version;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplateVersion#changeSummary
	 **/
	public static volatile SingularAttribute<ModelingTemplateVersion, String> changeSummary;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplateVersion#status
	 **/
	public static volatile SingularAttribute<ModelingTemplateVersion, String> status;

}

