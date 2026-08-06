package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(GovRuleVersion.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovRuleVersion_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String NOTES = "notes";
	public static final String BINDINGS = "bindings";
	public static final String CHECKSUM = "checksum";
	public static final String APPROVED_BY = "approvedBy";
	public static final String RULE = "rule";
	public static final String DEFINITION = "definition";
	public static final String ID = "id";
	public static final String APPROVED_AT = "approvedAt";
	public static final String VERSION = "version";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleVersion#notes
	 **/
	public static volatile SingularAttribute<GovRuleVersion, String> notes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleVersion#bindings
	 **/
	public static volatile SetAttribute<GovRuleVersion, GovRuleBinding> bindings;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleVersion#checksum
	 **/
	public static volatile SingularAttribute<GovRuleVersion, String> checksum;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleVersion#approvedBy
	 **/
	public static volatile SingularAttribute<GovRuleVersion, String> approvedBy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleVersion#rule
	 **/
	public static volatile SingularAttribute<GovRuleVersion, GovRule> rule;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleVersion#definition
	 **/
	public static volatile SingularAttribute<GovRuleVersion, String> definition;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleVersion#id
	 **/
	public static volatile SingularAttribute<GovRuleVersion, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleVersion#approvedAt
	 **/
	public static volatile SingularAttribute<GovRuleVersion, Instant> approvedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleVersion
	 **/
	public static volatile EntityType<GovRuleVersion> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleVersion#version
	 **/
	public static volatile SingularAttribute<GovRuleVersion, Integer> version;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovRuleVersion#status
	 **/
	public static volatile SingularAttribute<GovRuleVersion, String> status;

}

