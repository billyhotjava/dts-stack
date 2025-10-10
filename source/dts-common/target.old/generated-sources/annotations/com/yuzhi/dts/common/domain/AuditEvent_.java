package com.yuzhi.dts.common.domain;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;

@StaticMetamodel(AuditEvent.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class AuditEvent_ {

	public static final String ACTOR = "actor";
	public static final String TARGET_REF = "targetRef";
	public static final String CREATED_AT = "createdAt";
	public static final String IDEMPOTENCY_KEY = "idempotencyKey";
	public static final String ACTION = "action";
	public static final String TARGET_KIND = "targetKind";
	public static final String ID = "id";

	
	/**
	 * @see com.yuzhi.dts.common.domain.AuditEvent#actor
	 **/
	public static volatile SingularAttribute<AuditEvent, String> actor;
	
	/**
	 * @see com.yuzhi.dts.common.domain.AuditEvent#targetRef
	 **/
	public static volatile SingularAttribute<AuditEvent, String> targetRef;
	
	/**
	 * @see com.yuzhi.dts.common.domain.AuditEvent#createdAt
	 **/
	public static volatile SingularAttribute<AuditEvent, Instant> createdAt;
	
	/**
	 * @see com.yuzhi.dts.common.domain.AuditEvent#idempotencyKey
	 **/
	public static volatile SingularAttribute<AuditEvent, String> idempotencyKey;
	
	/**
	 * @see com.yuzhi.dts.common.domain.AuditEvent#action
	 **/
	public static volatile SingularAttribute<AuditEvent, String> action;
	
	/**
	 * @see com.yuzhi.dts.common.domain.AuditEvent#targetKind
	 **/
	public static volatile SingularAttribute<AuditEvent, String> targetKind;
	
	/**
	 * @see com.yuzhi.dts.common.domain.AuditEvent#id
	 **/
	public static volatile SingularAttribute<AuditEvent, Long> id;
	
	/**
	 * @see com.yuzhi.dts.common.domain.AuditEvent
	 **/
	public static volatile EntityType<AuditEvent> class_;

}

