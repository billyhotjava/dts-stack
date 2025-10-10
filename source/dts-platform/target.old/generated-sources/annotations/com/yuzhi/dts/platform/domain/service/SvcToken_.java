package com.yuzhi.dts.platform.domain.service;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(SvcToken.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SvcToken_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String TOKEN_HINT = "tokenHint";
	public static final String ID = "id";
	public static final String REVOKED = "revoked";
	public static final String TOKEN_HASH = "tokenHash";
	public static final String EXPIRES_AT = "expiresAt";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcToken#tokenHint
	 **/
	public static volatile SingularAttribute<SvcToken, String> tokenHint;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcToken#id
	 **/
	public static volatile SingularAttribute<SvcToken, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcToken#revoked
	 **/
	public static volatile SingularAttribute<SvcToken, Boolean> revoked;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcToken
	 **/
	public static volatile EntityType<SvcToken> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcToken#tokenHash
	 **/
	public static volatile SingularAttribute<SvcToken, String> tokenHash;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcToken#expiresAt
	 **/
	public static volatile SingularAttribute<SvcToken, Instant> expiresAt;

}

