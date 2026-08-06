package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(GovIndicatorReference.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovIndicatorReference_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String INDICATOR = "indicator";
	public static final String NOTES = "notes";
	public static final String REF_TYPE = "refType";
	public static final String ID = "id";
	public static final String REF_TARGET = "refTarget";
	public static final String REF_NAME = "refName";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorReference#indicator
	 **/
	public static volatile SingularAttribute<GovIndicatorReference, GovIndicatorDefinition> indicator;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorReference#notes
	 **/
	public static volatile SingularAttribute<GovIndicatorReference, String> notes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorReference#refType
	 **/
	public static volatile SingularAttribute<GovIndicatorReference, String> refType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorReference#id
	 **/
	public static volatile SingularAttribute<GovIndicatorReference, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorReference#refTarget
	 **/
	public static volatile SingularAttribute<GovIndicatorReference, String> refTarget;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorReference#refName
	 **/
	public static volatile SingularAttribute<GovIndicatorReference, String> refName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorReference
	 **/
	public static volatile EntityType<GovIndicatorReference> class_;

}

