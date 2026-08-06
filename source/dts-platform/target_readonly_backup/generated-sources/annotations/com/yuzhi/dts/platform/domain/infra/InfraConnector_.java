package com.yuzhi.dts.platform.domain.infra;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(InfraConnector.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class InfraConnector_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String CONNECTOR_KEY = "connectorKey";
	public static final String DISPLAY_ORDER = "displayOrder";
	public static final String DESCRIPTION = "description";
	public static final String COMPATIBILITY_PAYLOAD = "compatibilityPayload";
	public static final String DEFAULT_ENGINE = "defaultEngine";
	public static final String CAPABILITIES_PAYLOAD = "capabilitiesPayload";
	public static final String SOURCE_TYPE = "sourceType";
	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String CATEGORY = "category";
	public static final String SENSITIVE_FIELDS_PAYLOAD = "sensitiveFieldsPayload";
	public static final String CONFIG_SCHEMA_PAYLOAD = "configSchemaPayload";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraConnector#connectorKey
	 **/
	public static volatile SingularAttribute<InfraConnector, String> connectorKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraConnector#displayOrder
	 **/
	public static volatile SingularAttribute<InfraConnector, Integer> displayOrder;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraConnector#description
	 **/
	public static volatile SingularAttribute<InfraConnector, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraConnector#compatibilityPayload
	 **/
	public static volatile SingularAttribute<InfraConnector, String> compatibilityPayload;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraConnector#defaultEngine
	 **/
	public static volatile SingularAttribute<InfraConnector, String> defaultEngine;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraConnector#capabilitiesPayload
	 **/
	public static volatile SingularAttribute<InfraConnector, String> capabilitiesPayload;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraConnector#sourceType
	 **/
	public static volatile SingularAttribute<InfraConnector, String> sourceType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraConnector#name
	 **/
	public static volatile SingularAttribute<InfraConnector, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraConnector#id
	 **/
	public static volatile SingularAttribute<InfraConnector, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraConnector#category
	 **/
	public static volatile SingularAttribute<InfraConnector, String> category;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraConnector#sensitiveFieldsPayload
	 **/
	public static volatile SingularAttribute<InfraConnector, String> sensitiveFieldsPayload;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraConnector
	 **/
	public static volatile EntityType<InfraConnector> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraConnector#configSchemaPayload
	 **/
	public static volatile SingularAttribute<InfraConnector, String> configSchemaPayload;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraConnector#status
	 **/
	public static volatile SingularAttribute<InfraConnector, String> status;

}

