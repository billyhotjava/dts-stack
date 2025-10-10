package com.yuzhi.dts.admin.domain;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.net.InetAddress;
import java.time.Instant;

@StaticMetamodel(AuditEvent.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class AuditEvent_ {

	public static final String OCCURRED_AT = "occurredAt";
	public static final String RESOURCE_ID = "resourceId";
	public static final String CLIENT_AGENT = "clientAgent";
	public static final String PAYLOAD_HMAC = "payloadHmac";
	public static final String LAST_MODIFIED_DATE = "lastModifiedDate";
	public static final String ACTOR_ROLE = "actorRole";
	public static final String MODULE = "module";
	public static final String LAST_MODIFIED_BY = "lastModifiedBy";
	public static final String REQUEST_URI = "requestUri";
	public static final String HTTP_METHOD = "httpMethod";
	public static final String PAYLOAD_CIPHER = "payloadCipher";
	public static final String CHAIN_SIGNATURE = "chainSignature";
	public static final String EXTRA_TAGS = "extraTags";
	public static final String ACTOR = "actor";
	public static final String RESULT = "result";
	public static final String PAYLOAD_IV = "payloadIv";
	public static final String CREATED_DATE = "createdDate";
	public static final String CREATED_BY = "createdBy";
	public static final String CLIENT_IP = "clientIp";
	public static final String ACTION = "action";
	public static final String ID = "id";
	public static final String LATENCY_MS = "latencyMs";
	public static final String RESOURCE_TYPE = "resourceType";

	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#occurredAt
	 **/
	public static volatile SingularAttribute<AuditEvent, Instant> occurredAt;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#resourceId
	 **/
	public static volatile SingularAttribute<AuditEvent, String> resourceId;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#clientAgent
	 **/
	public static volatile SingularAttribute<AuditEvent, String> clientAgent;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#payloadHmac
	 **/
	public static volatile SingularAttribute<AuditEvent, String> payloadHmac;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#lastModifiedDate
	 **/
	public static volatile SingularAttribute<AuditEvent, Instant> lastModifiedDate;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#actorRole
	 **/
	public static volatile SingularAttribute<AuditEvent, String> actorRole;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#module
	 **/
	public static volatile SingularAttribute<AuditEvent, String> module;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#lastModifiedBy
	 **/
	public static volatile SingularAttribute<AuditEvent, String> lastModifiedBy;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#requestUri
	 **/
	public static volatile SingularAttribute<AuditEvent, String> requestUri;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#httpMethod
	 **/
	public static volatile SingularAttribute<AuditEvent, String> httpMethod;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#payloadCipher
	 **/
	public static volatile SingularAttribute<AuditEvent, byte[]> payloadCipher;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#chainSignature
	 **/
	public static volatile SingularAttribute<AuditEvent, String> chainSignature;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#extraTags
	 **/
	public static volatile SingularAttribute<AuditEvent, String> extraTags;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#actor
	 **/
	public static volatile SingularAttribute<AuditEvent, String> actor;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#result
	 **/
	public static volatile SingularAttribute<AuditEvent, String> result;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#payloadIv
	 **/
	public static volatile SingularAttribute<AuditEvent, byte[]> payloadIv;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#createdDate
	 **/
	public static volatile SingularAttribute<AuditEvent, Instant> createdDate;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#createdBy
	 **/
	public static volatile SingularAttribute<AuditEvent, String> createdBy;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#clientIp
	 **/
	public static volatile SingularAttribute<AuditEvent, InetAddress> clientIp;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#action
	 **/
	public static volatile SingularAttribute<AuditEvent, String> action;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#id
	 **/
	public static volatile SingularAttribute<AuditEvent, Long> id;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent
	 **/
	public static volatile EntityType<AuditEvent> class_;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#latencyMs
	 **/
	public static volatile SingularAttribute<AuditEvent, Integer> latencyMs;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AuditEvent#resourceType
	 **/
	public static volatile SingularAttribute<AuditEvent, String> resourceType;

}

