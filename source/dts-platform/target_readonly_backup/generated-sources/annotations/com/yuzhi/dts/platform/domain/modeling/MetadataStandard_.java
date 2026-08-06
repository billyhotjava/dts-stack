package com.yuzhi.dts.platform.domain.modeling;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(MetadataStandard.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class MetadataStandard_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String FIELD_NAME_CN = "fieldNameCn";
	public static final String NULLABLE = "nullable";
	public static final String DATA_LENGTH = "dataLength";
	public static final String DATA_SCALE = "dataScale";
	public static final String SOURCE_SYSTEM = "sourceSystem";
	public static final String DEFAULT_VALUE = "defaultValue";
	public static final String DATA_TYPE = "dataType";
	public static final String DESCRIPTION = "description";
	public static final String DATA_PRECISION = "dataPrecision";
	public static final String VERSION = "version";
	public static final String SECURITY_LEVEL = "securityLevel";
	public static final String IS_PK = "isPk";
	public static final String CODE_SET = "codeSet";
	public static final String DOMAIN = "domain";
	public static final String ID = "id";
	public static final String FIELD_NAME_EN = "fieldNameEn";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#fieldNameCn
	 **/
	public static volatile SingularAttribute<MetadataStandard, String> fieldNameCn;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#nullable
	 **/
	public static volatile SingularAttribute<MetadataStandard, Boolean> nullable;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#dataLength
	 **/
	public static volatile SingularAttribute<MetadataStandard, Integer> dataLength;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#dataScale
	 **/
	public static volatile SingularAttribute<MetadataStandard, Integer> dataScale;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#sourceSystem
	 **/
	public static volatile SingularAttribute<MetadataStandard, String> sourceSystem;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#defaultValue
	 **/
	public static volatile SingularAttribute<MetadataStandard, String> defaultValue;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#dataType
	 **/
	public static volatile SingularAttribute<MetadataStandard, String> dataType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#description
	 **/
	public static volatile SingularAttribute<MetadataStandard, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#dataPrecision
	 **/
	public static volatile SingularAttribute<MetadataStandard, Integer> dataPrecision;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#version
	 **/
	public static volatile SingularAttribute<MetadataStandard, Integer> version;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#securityLevel
	 **/
	public static volatile SingularAttribute<MetadataStandard, DataSecurityLevel> securityLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#isPk
	 **/
	public static volatile SingularAttribute<MetadataStandard, Boolean> isPk;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#codeSet
	 **/
	public static volatile SingularAttribute<MetadataStandard, String> codeSet;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#domain
	 **/
	public static volatile SingularAttribute<MetadataStandard, String> domain;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#id
	 **/
	public static volatile SingularAttribute<MetadataStandard, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard
	 **/
	public static volatile EntityType<MetadataStandard> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.MetadataStandard#fieldNameEn
	 **/
	public static volatile SingularAttribute<MetadataStandard, String> fieldNameEn;

}

