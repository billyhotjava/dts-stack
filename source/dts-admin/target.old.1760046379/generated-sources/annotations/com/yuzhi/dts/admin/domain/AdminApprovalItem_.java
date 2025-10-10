package com.yuzhi.dts.admin.domain;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(AdminApprovalItem.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class AdminApprovalItem_ {

	public static final String REQUEST = "request";
	public static final String PAYLOAD_JSON = "payloadJson";
	public static final String TARGET_ID = "targetId";
	public static final String TARGET_KIND = "targetKind";
	public static final String SEQ_NUMBER = "seqNumber";
	public static final String ID = "id";

	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalItem#request
	 **/
	public static volatile SingularAttribute<AdminApprovalItem, AdminApprovalRequest> request;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalItem#payloadJson
	 **/
	public static volatile SingularAttribute<AdminApprovalItem, String> payloadJson;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalItem#targetId
	 **/
	public static volatile SingularAttribute<AdminApprovalItem, String> targetId;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalItem#targetKind
	 **/
	public static volatile SingularAttribute<AdminApprovalItem, String> targetKind;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalItem#seqNumber
	 **/
	public static volatile SingularAttribute<AdminApprovalItem, Integer> seqNumber;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalItem#id
	 **/
	public static volatile SingularAttribute<AdminApprovalItem, Long> id;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalItem
	 **/
	public static volatile EntityType<AdminApprovalItem> class_;

}

