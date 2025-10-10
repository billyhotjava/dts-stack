package com.yuzhi.dts.admin.domain;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(AdminDataset.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class AdminDataset_ extends com.yuzhi.dts.admin.domain.AbstractAuditingEntity_ {

	public static final String BUSINESS_CODE = "businessCode";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String OWNER_ORG_ID = "ownerOrgId";
	public static final String ID = "id";
	public static final String ROW_COUNT = "rowCount";
	public static final String DATA_LEVEL = "dataLevel";
	public static final String IS_INSTITUTE_SHARED = "isInstituteShared";

	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminDataset#businessCode
	 **/
	public static volatile SingularAttribute<AdminDataset, String> businessCode;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminDataset#name
	 **/
	public static volatile SingularAttribute<AdminDataset, String> name;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminDataset#description
	 **/
	public static volatile SingularAttribute<AdminDataset, String> description;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminDataset#ownerOrgId
	 **/
	public static volatile SingularAttribute<AdminDataset, Long> ownerOrgId;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminDataset#id
	 **/
	public static volatile SingularAttribute<AdminDataset, Long> id;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminDataset#rowCount
	 **/
	public static volatile SingularAttribute<AdminDataset, Long> rowCount;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminDataset
	 **/
	public static volatile EntityType<AdminDataset> class_;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminDataset#dataLevel
	 **/
	public static volatile SingularAttribute<AdminDataset, String> dataLevel;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminDataset#isInstituteShared
	 **/
	public static volatile SingularAttribute<AdminDataset, Boolean> isInstituteShared;

}

