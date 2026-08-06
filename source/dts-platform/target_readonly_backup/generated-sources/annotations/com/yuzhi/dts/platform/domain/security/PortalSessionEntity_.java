package com.yuzhi.dts.platform.domain.security;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@StaticMetamodel(PortalSessionEntity.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class PortalSessionEntity_ {

	public static final String LAST_SEEN_AT = "lastSeenAt";
	public static final String REVOKED_BY_SESSION_ID = "revokedBySessionId";
	public static final String BROWSER_ID = "browserId";
	public static final String DISPLAY_NAME = "displayName";
	public static final String ROLES = "roles";
	public static final String SESSION_ID = "sessionId";
	public static final String ACCESS_TOKEN = "accessToken";
	public static final String EXPIRES_AT = "expiresAt";
	public static final String CREATED_AT = "createdAt";
	public static final String ADMIN_REFRESH_TOKEN_EXPIRES_AT = "adminRefreshTokenExpiresAt";
	public static final String PERMISSIONS = "permissions";
	public static final String PERSONNEL_LEVEL = "personnelLevel";
	public static final String NORMALIZED_USERNAME = "normalizedUsername";
	public static final String ADMIN_ACCESS_TOKEN_EXPIRES_AT = "adminAccessTokenExpiresAt";
	public static final String ADMIN_REFRESH_TOKEN = "adminRefreshToken";
	public static final String ADMIN_ACCESS_TOKEN = "adminAccessToken";
	public static final String ID = "id";
	public static final String REVOKED_AT = "revokedAt";
	public static final String DEPT_CODE = "deptCode";
	public static final String REVOKED_REASON = "revokedReason";
	public static final String USERNAME = "username";
	public static final String REFRESH_TOKEN = "refreshToken";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#lastSeenAt
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, Instant> lastSeenAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#revokedBySessionId
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, UUID> revokedBySessionId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#browserId
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, String> browserId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#displayName
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, String> displayName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#roles
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, List<String>> roles;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#sessionId
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, UUID> sessionId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#accessToken
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, String> accessToken;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#expiresAt
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, Instant> expiresAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#createdAt
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, Instant> createdAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#adminRefreshTokenExpiresAt
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, Instant> adminRefreshTokenExpiresAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#permissions
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, List<String>> permissions;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#personnelLevel
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, String> personnelLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#normalizedUsername
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, String> normalizedUsername;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#adminAccessTokenExpiresAt
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, Instant> adminAccessTokenExpiresAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#adminRefreshToken
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, String> adminRefreshToken;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#adminAccessToken
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, String> adminAccessToken;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#id
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity
	 **/
	public static volatile EntityType<PortalSessionEntity> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#revokedAt
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, Instant> revokedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#deptCode
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, String> deptCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#revokedReason
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, PortalSessionCloseReason> revokedReason;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#username
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, String> username;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.security.PortalSessionEntity#refreshToken
	 **/
	public static volatile SingularAttribute<PortalSessionEntity, String> refreshToken;

}

