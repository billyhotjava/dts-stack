package com.yuzhi.dts.platform.domain.modeling;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(ModelingGlossaryTerm.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class ModelingGlossaryTerm_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String OWNER = "owner";
	public static final String CODE = "code";
	public static final String ALIASES = "aliases";
	public static final String VERSION = "version";
	public static final String TAGS = "tags";
	public static final String DOMAIN = "domain";
	public static final String NAME = "name";
	public static final String DEFINITION = "definition";
	public static final String ID = "id";
	public static final String VERSION_NOTES = "versionNotes";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm#owner
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTerm, String> owner;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm#code
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTerm, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm#aliases
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTerm, String> aliases;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm#version
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTerm, String> version;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm#tags
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTerm, String> tags;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm#domain
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTerm, String> domain;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm#name
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTerm, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm#definition
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTerm, String> definition;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm#id
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTerm, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm#versionNotes
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTerm, String> versionNotes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm
	 **/
	public static volatile EntityType<ModelingGlossaryTerm> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm#ownerDept
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTerm, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm#status
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTerm, String> status;

}

