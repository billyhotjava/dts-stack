package com.yuzhi.dts.platform.domain.modeling;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(ModelingTemplate.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class ModelingTemplate_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String FIELDS_TEMPLATE = "fieldsTemplate";
	public static final String NAMING_RULE = "namingRule";
	public static final String METADATA_STANDARD_IDS = "metadataStandardIds";
	public static final String NAME = "name";
	public static final String REVIEW_CHECKLIST = "reviewChecklist";
	public static final String ID = "id";
	public static final String VERSION_NOTES = "versionNotes";
	public static final String VERSION = "version";
	public static final String LAYER = "layer";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplate#fieldsTemplate
	 **/
	public static volatile SingularAttribute<ModelingTemplate, String> fieldsTemplate;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplate#namingRule
	 **/
	public static volatile SingularAttribute<ModelingTemplate, String> namingRule;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplate#metadataStandardIds
	 **/
	public static volatile SingularAttribute<ModelingTemplate, String> metadataStandardIds;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplate#name
	 **/
	public static volatile SingularAttribute<ModelingTemplate, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplate#reviewChecklist
	 **/
	public static volatile SingularAttribute<ModelingTemplate, String> reviewChecklist;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplate#id
	 **/
	public static volatile SingularAttribute<ModelingTemplate, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplate#versionNotes
	 **/
	public static volatile SingularAttribute<ModelingTemplate, String> versionNotes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplate
	 **/
	public static volatile EntityType<ModelingTemplate> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplate#version
	 **/
	public static volatile SingularAttribute<ModelingTemplate, String> version;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplate#layer
	 **/
	public static volatile SingularAttribute<ModelingTemplate, String> layer;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingTemplate#status
	 **/
	public static volatile SingularAttribute<ModelingTemplate, String> status;

}

