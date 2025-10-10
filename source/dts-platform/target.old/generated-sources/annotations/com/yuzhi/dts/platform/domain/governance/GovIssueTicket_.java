package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(GovIssueTicket.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovIssueTicket_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SUMMARY = "summary";
	public static final String SEVERITY = "severity";
	public static final String OWNER = "owner";
	public static final String RESOLVED_AT = "resolvedAt";
	public static final String ASSIGNED_AT = "assignedAt";
	public static final String TITLE = "title";
	public static final String PRIORITY = "priority";
	public static final String RESOLUTION = "resolution";
	public static final String ASSIGNED_TO = "assignedTo";
	public static final String TAGS = "tags";
	public static final String DUE_AT = "dueAt";
	public static final String SOURCE_TYPE = "sourceType";
	public static final String ID = "id";
	public static final String COMPLIANCE_BATCH = "complianceBatch";
	public static final String ACTIONS = "actions";
	public static final String DATA_LEVEL = "dataLevel";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#summary
	 **/
	public static volatile SingularAttribute<GovIssueTicket, String> summary;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#severity
	 **/
	public static volatile SingularAttribute<GovIssueTicket, String> severity;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#owner
	 **/
	public static volatile SingularAttribute<GovIssueTicket, String> owner;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#resolvedAt
	 **/
	public static volatile SingularAttribute<GovIssueTicket, Instant> resolvedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#assignedAt
	 **/
	public static volatile SingularAttribute<GovIssueTicket, Instant> assignedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#title
	 **/
	public static volatile SingularAttribute<GovIssueTicket, String> title;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#priority
	 **/
	public static volatile SingularAttribute<GovIssueTicket, String> priority;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#resolution
	 **/
	public static volatile SingularAttribute<GovIssueTicket, String> resolution;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#assignedTo
	 **/
	public static volatile SingularAttribute<GovIssueTicket, String> assignedTo;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#tags
	 **/
	public static volatile SingularAttribute<GovIssueTicket, String> tags;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#dueAt
	 **/
	public static volatile SingularAttribute<GovIssueTicket, Instant> dueAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#sourceType
	 **/
	public static volatile SingularAttribute<GovIssueTicket, String> sourceType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#id
	 **/
	public static volatile SingularAttribute<GovIssueTicket, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#complianceBatch
	 **/
	public static volatile SingularAttribute<GovIssueTicket, GovComplianceBatch> complianceBatch;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket
	 **/
	public static volatile EntityType<GovIssueTicket> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#actions
	 **/
	public static volatile SetAttribute<GovIssueTicket, GovIssueAction> actions;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#dataLevel
	 **/
	public static volatile SingularAttribute<GovIssueTicket, String> dataLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueTicket#status
	 **/
	public static volatile SingularAttribute<GovIssueTicket, String> status;

}

