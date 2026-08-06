package com.yuzhi.dts.platform.domain.iam;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(IamSubjectDirectory.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class IamSubjectDirectory_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String DISPLAY_NAME = "displayName";
	public static final String EXTRA_JSON = "extraJson";
	public static final String ID = "id";
	public static final String SUBJECT_TYPE = "subjectType";
	public static final String SUBJECT_ID = "subjectId";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamSubjectDirectory#displayName
	 **/
	public static volatile SingularAttribute<IamSubjectDirectory, String> displayName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamSubjectDirectory#extraJson
	 **/
	public static volatile SingularAttribute<IamSubjectDirectory, String> extraJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamSubjectDirectory#id
	 **/
	public static volatile SingularAttribute<IamSubjectDirectory, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamSubjectDirectory
	 **/
	public static volatile EntityType<IamSubjectDirectory> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamSubjectDirectory#subjectType
	 **/
	public static volatile SingularAttribute<IamSubjectDirectory, String> subjectType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamSubjectDirectory#subjectId
	 **/
	public static volatile SingularAttribute<IamSubjectDirectory, String> subjectId;

}

