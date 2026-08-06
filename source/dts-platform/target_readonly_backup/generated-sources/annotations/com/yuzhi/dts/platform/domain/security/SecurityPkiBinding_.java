package com.yuzhi.dts.platform.domain.security;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(SecurityPkiBinding.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SecurityPkiBinding_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String LAST_SEEN_AT = "lastSeenAt";
	public static final String NOT_AFTER = "notAfter";
	public static final String ISSUER_DN = "issuerDn";
	public static final String ID = "id";
	public static final String USER_ID = "userId";
	public static final String CERT_SERIAL = "certSerial";
	public static final String NOT_BEFORE = "notBefore";
	public static final String USERNAME = "username";
	public static final String SUBJECT_DN = "subjectDn";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityPkiBinding#lastSeenAt
	 **/
	public static volatile SingularAttribute<SecurityPkiBinding, Instant> lastSeenAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityPkiBinding#notAfter
	 **/
	public static volatile SingularAttribute<SecurityPkiBinding, Instant> notAfter;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityPkiBinding#issuerDn
	 **/
	public static volatile SingularAttribute<SecurityPkiBinding, String> issuerDn;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityPkiBinding#id
	 **/
	public static volatile SingularAttribute<SecurityPkiBinding, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityPkiBinding
	 **/
	public static volatile EntityType<SecurityPkiBinding> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityPkiBinding#userId
	 **/
	public static volatile SingularAttribute<SecurityPkiBinding, String> userId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityPkiBinding#certSerial
	 **/
	public static volatile SingularAttribute<SecurityPkiBinding, String> certSerial;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityPkiBinding#notBefore
	 **/
	public static volatile SingularAttribute<SecurityPkiBinding, Instant> notBefore;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityPkiBinding#username
	 **/
	public static volatile SingularAttribute<SecurityPkiBinding, String> username;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityPkiBinding#subjectDn
	 **/
	public static volatile SingularAttribute<SecurityPkiBinding, String> subjectDn;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.SecurityPkiBinding#status
	 **/
	public static volatile SingularAttribute<SecurityPkiBinding, String> status;

}

