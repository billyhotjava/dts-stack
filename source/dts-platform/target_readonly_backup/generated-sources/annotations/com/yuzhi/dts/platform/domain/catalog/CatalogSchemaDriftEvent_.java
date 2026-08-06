package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogSchemaDriftEvent.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogSchemaDriftEvent_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String HANDLED_BY = "handledBy";
	public static final String TICKET_ASSIGNEE = "ticketAssignee";
	public static final String ADDED_COUNT = "addedCount";
	public static final String POLICY_MODE = "policyMode";
	public static final String HIVE_TABLE = "hiveTable";
	public static final String WORKFLOW_NOTE = "workflowNote";
	public static final String CHANGED_COUNT = "changedCount";
	public static final String TICKET_STATUS = "ticketStatus";
	public static final String INTEGRATION = "integration";
	public static final String DATASET_ID = "datasetId";
	public static final String ID = "id";
	public static final String RUN_ID = "runId";
	public static final String HANDLED_AT = "handledAt";
	public static final String HIVE_DATABASE = "hiveDatabase";
	public static final String REMOVED_COUNT = "removedCount";
	public static final String DETAILS_JSON = "detailsJson";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#handledBy
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, String> handledBy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#ticketAssignee
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, String> ticketAssignee;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#addedCount
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, Integer> addedCount;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#policyMode
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, String> policyMode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#hiveTable
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, String> hiveTable;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#workflowNote
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, String> workflowNote;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#changedCount
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, Integer> changedCount;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#ticketStatus
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, String> ticketStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#integration
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, String> integration;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#datasetId
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#id
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#runId
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, UUID> runId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent
	 **/
	public static volatile EntityType<CatalogSchemaDriftEvent> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#handledAt
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, Instant> handledAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#hiveDatabase
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, String> hiveDatabase;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#removedCount
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, Integer> removedCount;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogSchemaDriftEvent#detailsJson
	 **/
	public static volatile SingularAttribute<CatalogSchemaDriftEvent, String> detailsJson;

}

