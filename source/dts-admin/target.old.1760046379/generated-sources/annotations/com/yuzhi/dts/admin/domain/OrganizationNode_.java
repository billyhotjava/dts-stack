package com.yuzhi.dts.admin.domain;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(OrganizationNode.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class OrganizationNode_ extends com.yuzhi.dts.admin.domain.AbstractAuditingEntity_ {

	public static final String PARENT = "parent";
	public static final String PHONE = "phone";
	public static final String KEYCLOAK_GROUP_ID = "keycloakGroupId";
	public static final String CHILDREN = "children";
	public static final String CONTACT = "contact";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String ID = "id";
	public static final String DATA_LEVEL = "dataLevel";

	
	/**
	 * @see com.yuzhi.dts.admin.domain.OrganizationNode#parent
	 **/
	public static volatile SingularAttribute<OrganizationNode, OrganizationNode> parent;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.OrganizationNode#phone
	 **/
	public static volatile SingularAttribute<OrganizationNode, String> phone;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.OrganizationNode#keycloakGroupId
	 **/
	public static volatile SingularAttribute<OrganizationNode, String> keycloakGroupId;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.OrganizationNode#children
	 **/
	public static volatile ListAttribute<OrganizationNode, OrganizationNode> children;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.OrganizationNode#contact
	 **/
	public static volatile SingularAttribute<OrganizationNode, String> contact;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.OrganizationNode#name
	 **/
	public static volatile SingularAttribute<OrganizationNode, String> name;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.OrganizationNode#description
	 **/
	public static volatile SingularAttribute<OrganizationNode, String> description;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.OrganizationNode#id
	 **/
	public static volatile SingularAttribute<OrganizationNode, Long> id;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.OrganizationNode
	 **/
	public static volatile EntityType<OrganizationNode> class_;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.OrganizationNode#dataLevel
	 **/
	public static volatile SingularAttribute<OrganizationNode, String> dataLevel;

}

