package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(GovComplianceBatch.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovComplianceBatch_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SUMMARY = "summary";
	public static final String PROGRESS_PCT = "progressPct";
	public static final String METADATA_JSON = "metadataJson";
	public static final String STARTED_AT = "startedAt";
	public static final String TEMPLATE_CODE = "templateCode";
	public static final String TRIGGERED_TYPE = "triggeredType";
	public static final String FINISHED_AT = "finishedAt";
	public static final String EVIDENCE_REQUIRED = "evidenceRequired";
	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String ITEMS = "items";
	public static final String DATA_LEVEL = "dataLevel";
	public static final String SCHEDULED_AT = "scheduledAt";
	public static final String STATUS = "status";
	public static final String TRIGGERED_BY = "triggeredBy";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#summary
	 **/
	public static volatile SingularAttribute<GovComplianceBatch, String> summary;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#progressPct
	 **/
	public static volatile SingularAttribute<GovComplianceBatch, Integer> progressPct;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#metadataJson
	 **/
	public static volatile SingularAttribute<GovComplianceBatch, String> metadataJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#startedAt
	 **/
	public static volatile SingularAttribute<GovComplianceBatch, Instant> startedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#templateCode
	 **/
	public static volatile SingularAttribute<GovComplianceBatch, String> templateCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#triggeredType
	 **/
	public static volatile SingularAttribute<GovComplianceBatch, String> triggeredType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#finishedAt
	 **/
	public static volatile SingularAttribute<GovComplianceBatch, Instant> finishedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#evidenceRequired
	 **/
	public static volatile SingularAttribute<GovComplianceBatch, Boolean> evidenceRequired;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#name
	 **/
	public static volatile SingularAttribute<GovComplianceBatch, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#id
	 **/
	public static volatile SingularAttribute<GovComplianceBatch, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch
	 **/
	public static volatile EntityType<GovComplianceBatch> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#items
	 **/
	public static volatile SetAttribute<GovComplianceBatch, GovComplianceBatchItem> items;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#dataLevel
	 **/
	public static volatile SingularAttribute<GovComplianceBatch, String> dataLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#scheduledAt
	 **/
	public static volatile SingularAttribute<GovComplianceBatch, Instant> scheduledAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#status
	 **/
	public static volatile SingularAttribute<GovComplianceBatch, String> status;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatch#triggeredBy
	 **/
	public static volatile SingularAttribute<GovComplianceBatch, String> triggeredBy;

}

