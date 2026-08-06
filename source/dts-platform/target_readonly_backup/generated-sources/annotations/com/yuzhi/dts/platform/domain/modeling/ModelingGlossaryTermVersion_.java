package com.yuzhi.dts.platform.domain.modeling;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(ModelingGlossaryTermVersion.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class ModelingGlossaryTermVersion_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String RELEASED_AT = "releasedAt";
	public static final String SNAPSHOT_JSON = "snapshotJson";
	public static final String TERM = "term";
	public static final String ID = "id";
	public static final String VERSION = "version";
	public static final String CHANGE_SUMMARY = "changeSummary";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermVersion#releasedAt
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTermVersion, Instant> releasedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermVersion#snapshotJson
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTermVersion, String> snapshotJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermVersion#term
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTermVersion, ModelingGlossaryTerm> term;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermVersion#id
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTermVersion, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermVersion
	 **/
	public static volatile EntityType<ModelingGlossaryTermVersion> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermVersion#version
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTermVersion, String> version;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermVersion#changeSummary
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTermVersion, String> changeSummary;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermVersion#status
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTermVersion, String> status;

}

