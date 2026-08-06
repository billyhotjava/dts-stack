package com.yuzhi.dts.platform.domain.security;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(SecurityBaselineRemediation.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SecurityBaselineRemediation_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String NOTES = "notes";
	public static final String CHECK_KEY = "checkKey";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBaselineRemediation#notes
	 **/
	public static volatile SingularAttribute<SecurityBaselineRemediation, String> notes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBaselineRemediation
	 **/
	public static volatile EntityType<SecurityBaselineRemediation> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBaselineRemediation#checkKey
	 **/
	public static volatile SingularAttribute<SecurityBaselineRemediation, String> checkKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityBaselineRemediation#status
	 **/
	public static volatile SingularAttribute<SecurityBaselineRemediation, String> status;

}

