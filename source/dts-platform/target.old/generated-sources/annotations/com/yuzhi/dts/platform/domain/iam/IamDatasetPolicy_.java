package com.yuzhi.dts.platform.domain.iam;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(IamDatasetPolicy.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class IamDatasetPolicy_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String FIELD_NAME = "fieldName";
	public static final String DATASET_NAME = "datasetName";
	public static final String DESCRIPTION = "description";
	public static final String SOURCE = "source";
	public static final String VALID_FROM = "validFrom";
	public static final String SUBJECT_TYPE = "subjectType";
	public static final String SUBJECT_ID = "subjectId";
	public static final String ROW_EXPRESSION = "rowExpression";
	public static final String SCOPE = "scope";
	public static final String EFFECT = "effect";
	public static final String DATASET_ID = "datasetId";
	public static final String ID = "id";
	public static final String SUBJECT_NAME = "subjectName";
	public static final String VALID_TO = "validTo";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy#fieldName
	 **/
	public static volatile SingularAttribute<IamDatasetPolicy, String> fieldName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy#datasetName
	 **/
	public static volatile SingularAttribute<IamDatasetPolicy, String> datasetName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy#description
	 **/
	public static volatile SingularAttribute<IamDatasetPolicy, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy#source
	 **/
	public static volatile SingularAttribute<IamDatasetPolicy, String> source;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy#validFrom
	 **/
	public static volatile SingularAttribute<IamDatasetPolicy, Instant> validFrom;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy#subjectType
	 **/
	public static volatile SingularAttribute<IamDatasetPolicy, String> subjectType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy#subjectId
	 **/
	public static volatile SingularAttribute<IamDatasetPolicy, String> subjectId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy#rowExpression
	 **/
	public static volatile SingularAttribute<IamDatasetPolicy, String> rowExpression;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy#scope
	 **/
	public static volatile SingularAttribute<IamDatasetPolicy, String> scope;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy#effect
	 **/
	public static volatile SingularAttribute<IamDatasetPolicy, String> effect;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy#datasetId
	 **/
	public static volatile SingularAttribute<IamDatasetPolicy, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy#id
	 **/
	public static volatile SingularAttribute<IamDatasetPolicy, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy
	 **/
	public static volatile EntityType<IamDatasetPolicy> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy#subjectName
	 **/
	public static volatile SingularAttribute<IamDatasetPolicy, String> subjectName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy#validTo
	 **/
	public static volatile SingularAttribute<IamDatasetPolicy, Instant> validTo;

}

