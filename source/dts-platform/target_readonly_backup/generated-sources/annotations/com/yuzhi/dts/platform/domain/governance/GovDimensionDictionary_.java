package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(GovDimensionDictionary.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovDimensionDictionary_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String OWNER = "owner";
	public static final String CODE = "code";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String ID = "id";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String DATA_LEVEL = "dataLevel";
	public static final String STATUS = "status";
	public static final String TAGS = "tags";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary#owner
	 **/
	public static volatile SingularAttribute<GovDimensionDictionary, String> owner;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary#code
	 **/
	public static volatile SingularAttribute<GovDimensionDictionary, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary#name
	 **/
	public static volatile SingularAttribute<GovDimensionDictionary, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary#description
	 **/
	public static volatile SingularAttribute<GovDimensionDictionary, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary#id
	 **/
	public static volatile SingularAttribute<GovDimensionDictionary, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary
	 **/
	public static volatile EntityType<GovDimensionDictionary> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary#ownerDept
	 **/
	public static volatile SingularAttribute<GovDimensionDictionary, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary#dataLevel
	 **/
	public static volatile SingularAttribute<GovDimensionDictionary, String> dataLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary#status
	 **/
	public static volatile SingularAttribute<GovDimensionDictionary, String> status;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary#tags
	 **/
	public static volatile SingularAttribute<GovDimensionDictionary, String> tags;

}

