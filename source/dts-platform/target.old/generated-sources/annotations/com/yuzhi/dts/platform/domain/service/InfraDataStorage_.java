package com.yuzhi.dts.platform.domain.service;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(InfraDataStorage.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class InfraDataStorage_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SECURE_KEY_VERSION = "secureKeyVersion";
	public static final String SECURE_IV = "secureIv";
	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";
	public static final String LOCATION = "location";
	public static final String SECURE_PROPS = "secureProps";
	public static final String ID = "id";
	public static final String TYPE = "type";
	public static final String PROPS = "props";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataStorage#secureKeyVersion
	 **/
	public static volatile SingularAttribute<InfraDataStorage, String> secureKeyVersion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataStorage#secureIv
	 **/
	public static volatile SingularAttribute<InfraDataStorage, byte[]> secureIv;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataStorage#name
	 **/
	public static volatile SingularAttribute<InfraDataStorage, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataStorage#description
	 **/
	public static volatile SingularAttribute<InfraDataStorage, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataStorage#location
	 **/
	public static volatile SingularAttribute<InfraDataStorage, String> location;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataStorage#secureProps
	 **/
	public static volatile SingularAttribute<InfraDataStorage, byte[]> secureProps;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataStorage#id
	 **/
	public static volatile SingularAttribute<InfraDataStorage, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataStorage#type
	 **/
	public static volatile SingularAttribute<InfraDataStorage, String> type;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataStorage
	 **/
	public static volatile EntityType<InfraDataStorage> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.InfraDataStorage#props
	 **/
	public static volatile SingularAttribute<InfraDataStorage, String> props;

}

