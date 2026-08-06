package com.yuzhi.dts.platform.domain.infra;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(InfraOdsTableMapping.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class InfraOdsTableMapping_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String OWNER = "owner";
	public static final String ENTITY_CODE = "entityCode";
	public static final String BIZ_CODE = "bizCode";
	public static final String DESCRIPTION = "description";
	public static final String ODS_SCHEMA = "odsSchema";
	public static final String ODS_TABLE = "odsTable";
	public static final String STREAM_NAME = "streamName";
	public static final String ENABLED = "enabled";
	public static final String SYSTEM_CODE = "systemCode";
	public static final String CONNECTION_ID = "connectionId";
	public static final String DATASET_ID = "datasetId";
	public static final String STREAM_NAMESPACE = "streamNamespace";
	public static final String ID = "id";
	public static final String OWNER_DEPT = "ownerDept";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping#owner
	 **/
	public static volatile SingularAttribute<InfraOdsTableMapping, String> owner;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping#entityCode
	 **/
	public static volatile SingularAttribute<InfraOdsTableMapping, String> entityCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping#bizCode
	 **/
	public static volatile SingularAttribute<InfraOdsTableMapping, String> bizCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping#description
	 **/
	public static volatile SingularAttribute<InfraOdsTableMapping, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping#odsSchema
	 **/
	public static volatile SingularAttribute<InfraOdsTableMapping, String> odsSchema;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping#odsTable
	 **/
	public static volatile SingularAttribute<InfraOdsTableMapping, String> odsTable;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping#streamName
	 **/
	public static volatile SingularAttribute<InfraOdsTableMapping, String> streamName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping#enabled
	 **/
	public static volatile SingularAttribute<InfraOdsTableMapping, Boolean> enabled;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping#systemCode
	 **/
	public static volatile SingularAttribute<InfraOdsTableMapping, String> systemCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping#connectionId
	 **/
	public static volatile SingularAttribute<InfraOdsTableMapping, UUID> connectionId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping#datasetId
	 **/
	public static volatile SingularAttribute<InfraOdsTableMapping, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping#streamNamespace
	 **/
	public static volatile SingularAttribute<InfraOdsTableMapping, String> streamNamespace;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping#id
	 **/
	public static volatile SingularAttribute<InfraOdsTableMapping, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping
	 **/
	public static volatile EntityType<InfraOdsTableMapping> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping#ownerDept
	 **/
	public static volatile SingularAttribute<InfraOdsTableMapping, String> ownerDept;

}

