package com.yuzhi.dts.platform.domain.modeling;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(ModelingGlossaryTermReview.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class ModelingGlossaryTermReview_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String REVIEW_NOTES = "reviewNotes";
	public static final String REVIEWED_AT = "reviewedAt";
	public static final String TERM = "term";
	public static final String ID = "id";
	public static final String REVIEWER = "reviewer";
	public static final String VERSION = "version";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermReview#reviewNotes
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTermReview, String> reviewNotes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermReview#reviewedAt
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTermReview, Instant> reviewedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermReview#term
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTermReview, ModelingGlossaryTerm> term;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermReview#id
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTermReview, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermReview#reviewer
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTermReview, String> reviewer;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermReview
	 **/
	public static volatile EntityType<ModelingGlossaryTermReview> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermReview#version
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTermReview, String> version;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTermReview#status
	 **/
	public static volatile SingularAttribute<ModelingGlossaryTermReview, String> status;

}

