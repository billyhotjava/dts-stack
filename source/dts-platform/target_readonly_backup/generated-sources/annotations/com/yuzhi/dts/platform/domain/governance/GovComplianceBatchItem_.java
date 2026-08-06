package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(GovComplianceBatchItem.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovComplianceBatchItem_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String QUALITY_RUN = "qualityRun";
	public static final String SEVERITY = "severity";
	public static final String CONCLUSION = "conclusion";
	public static final String EVIDENCE_REF = "evidenceRef";
	public static final String RULE_VERSION = "ruleVersion";
	public static final String BATCH = "batch";
	public static final String RULE = "rule";
	public static final String DATASET_ID = "datasetId";
	public static final String ID = "id";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatchItem#qualityRun
	 **/
	public static volatile SingularAttribute<GovComplianceBatchItem, GovQualityRun> qualityRun;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatchItem#severity
	 **/
	public static volatile SingularAttribute<GovComplianceBatchItem, String> severity;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatchItem#conclusion
	 **/
	public static volatile SingularAttribute<GovComplianceBatchItem, String> conclusion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatchItem#evidenceRef
	 **/
	public static volatile SingularAttribute<GovComplianceBatchItem, String> evidenceRef;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatchItem#ruleVersion
	 **/
	public static volatile SingularAttribute<GovComplianceBatchItem, GovRuleVersion> ruleVersion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatchItem#batch
	 **/
	public static volatile SingularAttribute<GovComplianceBatchItem, GovComplianceBatch> batch;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatchItem#rule
	 **/
	public static volatile SingularAttribute<GovComplianceBatchItem, GovRule> rule;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatchItem#datasetId
	 **/
	public static volatile SingularAttribute<GovComplianceBatchItem, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatchItem#id
	 **/
	public static volatile SingularAttribute<GovComplianceBatchItem, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatchItem
	 **/
	public static volatile EntityType<GovComplianceBatchItem> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceBatchItem#status
	 **/
	public static volatile SingularAttribute<GovComplianceBatchItem, String> status;

}

