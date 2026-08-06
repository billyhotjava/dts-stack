package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(StdCodeValue.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class StdCodeValue_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String CODE_TYPE_ID = "codeTypeId";
	public static final String ITEM_ID = "itemId";
	public static final String IS_DEFAULT = "isDefault";
	public static final String PARENT_CODE = "parentCode";
	public static final String CODE_NAME = "codeName";
	public static final String DESCRIPTION = "description";
	public static final String SORT_NUM = "sortNum";
	public static final String CODE_VALUE = "codeValue";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeValue#codeTypeId
	 **/
	public static volatile SingularAttribute<StdCodeValue, String> codeTypeId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeValue#itemId
	 **/
	public static volatile SingularAttribute<StdCodeValue, Long> itemId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeValue#isDefault
	 **/
	public static volatile SingularAttribute<StdCodeValue, Boolean> isDefault;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeValue#parentCode
	 **/
	public static volatile SingularAttribute<StdCodeValue, String> parentCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeValue#codeName
	 **/
	public static volatile SingularAttribute<StdCodeValue, String> codeName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeValue#description
	 **/
	public static volatile SingularAttribute<StdCodeValue, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeValue#sortNum
	 **/
	public static volatile SingularAttribute<StdCodeValue, Integer> sortNum;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeValue
	 **/
	public static volatile EntityType<StdCodeValue> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.StdCodeValue#codeValue
	 **/
	public static volatile SingularAttribute<StdCodeValue, String> codeValue;

}

