package com.yuzhi.dts.admin.domain;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.List;

@StaticMetamodel(AdminKeycloakUser.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class AdminKeycloakUser_ extends com.yuzhi.dts.admin.domain.AbstractAuditingEntity_ {

	public static final String DATA_LEVELS = "dataLevels";
	public static final String LAST_SYNC_AT = "lastSyncAt";
	public static final String PERSON_SECURITY_LEVEL = "personSecurityLevel";
	public static final String FULL_NAME = "fullName";
	public static final String ENABLED = "enabled";
	public static final String REALM_ROLES = "realmRoles";
	public static final String KEYCLOAK_ID = "keycloakId";
	public static final String PHONE = "phone";
	public static final String GROUP_PATHS = "groupPaths";
	public static final String ID = "id";
	public static final String EMAIL = "email";
	public static final String USERNAME = "username";

	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminKeycloakUser#dataLevels
	 **/
	public static volatile SingularAttribute<AdminKeycloakUser, List<String>> dataLevels;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminKeycloakUser#lastSyncAt
	 **/
	public static volatile SingularAttribute<AdminKeycloakUser, Instant> lastSyncAt;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminKeycloakUser#personSecurityLevel
	 **/
	public static volatile SingularAttribute<AdminKeycloakUser, String> personSecurityLevel;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminKeycloakUser#fullName
	 **/
	public static volatile SingularAttribute<AdminKeycloakUser, String> fullName;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminKeycloakUser#enabled
	 **/
	public static volatile SingularAttribute<AdminKeycloakUser, Boolean> enabled;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminKeycloakUser#realmRoles
	 **/
	public static volatile SingularAttribute<AdminKeycloakUser, List<String>> realmRoles;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminKeycloakUser#keycloakId
	 **/
	public static volatile SingularAttribute<AdminKeycloakUser, String> keycloakId;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminKeycloakUser#phone
	 **/
	public static volatile SingularAttribute<AdminKeycloakUser, String> phone;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminKeycloakUser#groupPaths
	 **/
	public static volatile SingularAttribute<AdminKeycloakUser, List<String>> groupPaths;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminKeycloakUser#id
	 **/
	public static volatile SingularAttribute<AdminKeycloakUser, Long> id;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminKeycloakUser
	 **/
	public static volatile EntityType<AdminKeycloakUser> class_;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminKeycloakUser#email
	 **/
	public static volatile SingularAttribute<AdminKeycloakUser, String> email;
	
	/**
	 * @see com.yuzhi.dts.admin.domain.AdminKeycloakUser#username
	 **/
	public static volatile SingularAttribute<AdminKeycloakUser, String> username;

}

