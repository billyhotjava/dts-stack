package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogDatasetAccessRequest.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogDatasetAccessRequest_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String REASON = "reason";
	public static final String TARGET_NAME = "targetName";
	public static final String REQUESTER_ID = "requesterId";
	public static final String TARGET_USERNAME = "targetUsername";
	public static final String DATASET_NAME = "datasetName";
	public static final String TARGET_USER_ID = "targetUserId";
	public static final String CAN_QUERY = "canQuery";
	public static final String VALID_FROM = "validFrom";
	public static final String DECIDED_BY = "decidedBy";
	public static final String CLASSIFICATION = "classification";
	public static final String WAREHOUSE_LAYER = "warehouseLayer";
	public static final String REQUESTER_USERNAME = "requesterUsername";
	public static final String REQUESTER_DEPT = "requesterDept";
	public static final String TARGET_DEPT = "targetDept";
	public static final String CAN_PREVIEW = "canPreview";
	public static final String DECISION_NOTES = "decisionNotes";
	public static final String REQUESTER_NAME = "requesterName";
	public static final String DECIDED_AT = "decidedAt";
	public static final String DATASET_ID = "datasetId";
	public static final String ID = "id";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String STATUS = "status";
	public static final String VALID_TO = "validTo";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#reason
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> reason;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#targetName
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> targetName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#requesterId
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> requesterId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#targetUsername
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> targetUsername;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#datasetName
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> datasetName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#targetUserId
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> targetUserId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#canQuery
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, Boolean> canQuery;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#validFrom
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, Instant> validFrom;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#decidedBy
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> decidedBy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#classification
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> classification;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#warehouseLayer
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> warehouseLayer;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#requesterUsername
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> requesterUsername;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#requesterDept
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> requesterDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#targetDept
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> targetDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#canPreview
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, Boolean> canPreview;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#decisionNotes
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> decisionNotes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#requesterName
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> requesterName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#decidedAt
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, Instant> decidedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#datasetId
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#id
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest
	 **/
	public static volatile EntityType<CatalogDatasetAccessRequest> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#ownerDept
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#status
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, String> status;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogDatasetAccessRequest#validTo
	 **/
	public static volatile SingularAttribute<CatalogDatasetAccessRequest, Instant> validTo;

}

