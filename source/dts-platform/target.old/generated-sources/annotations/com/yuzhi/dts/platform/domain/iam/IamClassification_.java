package com.yuzhi.dts.platform.domain.iam;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(IamClassification.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class IamClassification_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String CODE = "code";
	public static final String ID = "id";
	public static final String LABEL = "label";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamClassification#code
	 **/
	public static volatile SingularAttribute<IamClassification, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamClassification#id
	 **/
	public static volatile SingularAttribute<IamClassification, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamClassification#label
	 **/
	public static volatile SingularAttribute<IamClassification, String> label;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamClassification
	 **/
	public static volatile EntityType<IamClassification> class_;

}

