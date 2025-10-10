package com.yuzhi.dts.admin.domain;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(AdminRoleAssignment.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class AdminRoleAssignment_ extends com.yuzhi.dts.admin.domain.AbstractAuditingEntity_ {

	public static final String SCOPE_ORG_ID = "scopeOrgId";
	public static final String ROLE = "role";
	public static final String OPERATIONS_CSV = "operationsCsv";
	public static final String DATASET_IDS_CSV = "datasetIdsCsv";
	public static final String DISPLAY_NAME = "displayName";
	public static final String USER_SECURITY_LEVEL = "userSecurityLevel";
	public static final String ID = "id";
	public static final String USERNAME = "username";

	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminRoleAssignment#scopeOrgId
	 **/
	public static volatile SingularAttribute<AdminRoleAssignment, Long> scopeOrgId;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminRoleAssignment#role
	 **/
	public static volatile SingularAttribute<AdminRoleAssignment, String> role;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminRoleAssignment#operationsCsv
	 **/
	public static volatile SingularAttribute<AdminRoleAssignment, String> operationsCsv;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminRoleAssignment#datasetIdsCsv
	 **/
	public static volatile SingularAttribute<AdminRoleAssignment, String> datasetIdsCsv;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminRoleAssignment#displayName
	 **/
	public static volatile SingularAttribute<AdminRoleAssignment, String> displayName;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminRoleAssignment#userSecurityLevel
	 **/
	public static volatile SingularAttribute<AdminRoleAssignment, String> userSecurityLevel;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminRoleAssignment#id
	 **/
	public static volatile SingularAttribute<AdminRoleAssignment, Long> id;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminRoleAssignment
	 **/
	public static volatile EntityType<AdminRoleAssignment> class_;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminRoleAssignment#username
	 **/
	public static volatile SingularAttribute<AdminRoleAssignment, String> username;

}

