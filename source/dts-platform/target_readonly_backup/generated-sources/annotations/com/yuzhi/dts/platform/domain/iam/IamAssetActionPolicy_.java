package com.yuzhi.dts.platform.domain.iam;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(IamAssetActionPolicy.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class IamAssetActionPolicy_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String RESOURCE_ID = "resourceId";
	public static final String RESOURCE_NAME = "resourceName";
	public static final String SOURCE = "source";
	public static final String VALID_FROM = "validFrom";
	public static final String SUBJECT_TYPE = "subjectType";
	public static final String SUBJECT_ID = "subjectId";
	public static final String EFFECT = "effect";
	public static final String ACTION = "action";
	public static final String ID = "id";
	public static final String SUBJECT_NAME = "subjectName";
	public static final String RESOURCE_TYPE = "resourceType";
	public static final String VALID_TO = "validTo";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy#resourceId
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicy, String> resourceId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy#resourceName
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicy, String> resourceName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy#source
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicy, String> source;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy#validFrom
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicy, Instant> validFrom;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy#subjectType
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicy, String> subjectType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy#subjectId
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicy, String> subjectId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy#effect
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicy, String> effect;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy#action
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicy, String> action;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy#id
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicy, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy
	 **/
	public static volatile EntityType<IamAssetActionPolicy> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy#subjectName
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicy, String> subjectName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy#resourceType
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicy, String> resourceType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy#validTo
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicy, Instant> validTo;

}

