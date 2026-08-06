package com.yuzhi.dts.platform.domain.iam;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(IamDept.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class IamDept_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String PATH = "path";
	public static final String CODE = "code";
	public static final String PARENT_CODE = "parentCode";
	public static final String NAME_ZH = "nameZh";
	public static final String ID = "id";
	public static final String NAME_EN = "nameEn";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDept#path
	 **/
	public static volatile SingularAttribute<IamDept, String> path;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDept#code
	 **/
	public static volatile SingularAttribute<IamDept, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDept#parentCode
	 **/
	public static volatile SingularAttribute<IamDept, String> parentCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDept#nameZh
	 **/
	public static volatile SingularAttribute<IamDept, String> nameZh;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDept#id
	 **/
	public static volatile SingularAttribute<IamDept, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDept#nameEn
	 **/
	public static volatile SingularAttribute<IamDept, String> nameEn;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamDept
	 **/
	public static volatile EntityType<IamDept> class_;

}

