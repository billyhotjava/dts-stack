package com.yuzhi.dts.admin.domain;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;

@StaticMetamodel(AdminApprovalRequest.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class AdminApprovalRequest_ extends com.yuzhi.dts.admin.domain.AbstractAuditingEntity_ {

	public static final String REQUESTER = "requester";
	public static final String APPROVER = "approver";
	public static final String REASON = "reason";
	public static final String DECISION_NOTE = "decisionNote";
	public static final String RETRY_COUNT = "retryCount";
	public static final String DECIDED_AT = "decidedAt";
	public static final String ERROR_MESSAGE = "errorMessage";
	public static final String ID = "id";
	public static final String TYPE = "type";
	public static final String ITEMS = "items";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalRequest#requester
	 **/
	public static volatile SingularAttribute<AdminApprovalRequest, String> requester;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalRequest#approver
	 **/
	public static volatile SingularAttribute<AdminApprovalRequest, String> approver;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalRequest#reason
	 **/
	public static volatile SingularAttribute<AdminApprovalRequest, String> reason;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalRequest#decisionNote
	 **/
	public static volatile SingularAttribute<AdminApprovalRequest, String> decisionNote;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalRequest#retryCount
	 **/
	public static volatile SingularAttribute<AdminApprovalRequest, Integer> retryCount;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalRequest#decidedAt
	 **/
	public static volatile SingularAttribute<AdminApprovalRequest, Instant> decidedAt;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalRequest#errorMessage
	 **/
	public static volatile SingularAttribute<AdminApprovalRequest, String> errorMessage;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalRequest#id
	 **/
	public static volatile SingularAttribute<AdminApprovalRequest, Long> id;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalRequest#type
	 **/
	public static volatile SingularAttribute<AdminApprovalRequest, String> type;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalRequest
	 **/
	public static volatile EntityType<AdminApprovalRequest> class_;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalRequest#items
	 **/
	public static volatile ListAttribute<AdminApprovalRequest, AdminApprovalItem> items;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminApprovalRequest#status
	 **/
	public static volatile SingularAttribute<AdminApprovalRequest, String> status;

}

