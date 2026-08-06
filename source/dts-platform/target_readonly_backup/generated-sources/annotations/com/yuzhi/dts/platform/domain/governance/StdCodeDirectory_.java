package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(StdCodeDirectory.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class StdCodeDirectory_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String CODE_TYPE_ID = "codeTypeId";
	public static final String BIZ_CATALOG = "bizCatalog";
	public static final String CODE_TYPE_NAME = "codeTypeName";
	public static final String DATA_TYPE = "dataType";
	public static final String CODE_TYPE_CODE = "codeTypeCode";
	public static final String STD_LEVEL = "stdLevel";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String VERSION = "version";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeDirectory#codeTypeId
	 **/
	public static volatile SingularAttribute<StdCodeDirectory, String> codeTypeId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeDirectory#bizCatalog
	 **/
	public static volatile SingularAttribute<StdCodeDirectory, String> bizCatalog;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeDirectory#codeTypeName
	 **/
	public static volatile SingularAttribute<StdCodeDirectory, String> codeTypeName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeDirectory#dataType
	 **/
	public static volatile SingularAttribute<StdCodeDirectory, String> dataType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeDirectory#codeTypeCode
	 **/
	public static volatile SingularAttribute<StdCodeDirectory, String> codeTypeCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeDirectory#stdLevel
	 **/
	public static volatile SingularAttribute<StdCodeDirectory, String> stdLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeDirectory
	 **/
	public static volatile EntityType<StdCodeDirectory> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeDirectory#ownerDept
	 **/
	public static volatile SingularAttribute<StdCodeDirectory, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeDirectory#version
	 **/
	public static volatile SingularAttribute<StdCodeDirectory, String> version;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeDirectory#status
	 **/
	public static volatile SingularAttribute<StdCodeDirectory, Integer> status;

}

