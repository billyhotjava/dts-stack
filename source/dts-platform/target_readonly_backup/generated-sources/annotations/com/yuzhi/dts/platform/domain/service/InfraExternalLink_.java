package com.yuzhi.dts.platform.domain.service;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(InfraExternalLink.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class InfraExternalLink_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String STATUS_API_URL = "statusApiUrl";
	public static final String NAME = "name";
	public static final String STATUS_API_ENABLED = "statusApiEnabled";
	public static final String ENTRY_KEY = "entryKey";
	public static final String DESCRIPTION = "description";
	public static final String ID = "id";
	public static final String URL = "url";
	public static final String ENABLED = "enabled";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraExternalLink#statusApiUrl
	 **/
	public static volatile SingularAttribute<InfraExternalLink, String> statusApiUrl;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraExternalLink#name
	 **/
	public static volatile SingularAttribute<InfraExternalLink, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraExternalLink#statusApiEnabled
	 **/
	public static volatile SingularAttribute<InfraExternalLink, Boolean> statusApiEnabled;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraExternalLink#entryKey
	 **/
	public static volatile SingularAttribute<InfraExternalLink, String> entryKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraExternalLink#description
	 **/
	public static volatile SingularAttribute<InfraExternalLink, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraExternalLink#id
	 **/
	public static volatile SingularAttribute<InfraExternalLink, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraExternalLink
	 **/
	public static volatile EntityType<InfraExternalLink> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraExternalLink#url
	 **/
	public static volatile SingularAttribute<InfraExternalLink, String> url;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraExternalLink#enabled
	 **/
	public static volatile SingularAttribute<InfraExternalLink, Boolean> enabled;

}

