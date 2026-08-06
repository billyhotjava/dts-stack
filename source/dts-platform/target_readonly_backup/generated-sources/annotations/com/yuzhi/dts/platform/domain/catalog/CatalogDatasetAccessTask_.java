package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogDatasetAccessTask.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogDatasetAccessTask_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String DECISION_NOTES = "decisionNotes";
	public static final String REQUEST_ID = "requestId";
	public static final String DECIDED_AT = "decidedAt";
	public static final String STEP_ORDER = "stepOrder";
	public static final String APPROVER_ROLE = "approverRole";
	public static final String ID = "id";
	public static final String DECIDED_BY = "decidedBy";
	public static final String DEPT_CODE = "deptCode";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask#decisionNotes
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessTask, String> decisionNotes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask#requestId
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessTask, UUID> requestId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask#decidedAt
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessTask, Instant> decidedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask#stepOrder
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessTask, Integer> stepOrder;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask#approverRole
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessTask, String> approverRole;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask#id
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessTask, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask#decidedBy
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessTask, String> decidedBy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask
	 **/
	public static volatile EntityType<CatalogDatasetAccessTask> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask#deptCode
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessTask, String> deptCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessTask#status
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessTask, String> status;

}

