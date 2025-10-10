package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(GovComplianceCheck.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovComplianceCheck_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String CHECKED_AT = "checkedAt";
	public static final String ID = "id";
	public static final String DETAIL = "detail";
	public static final String RULE_ID = "ruleId";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceCheck#checkedAt
	 **/
	public static volatile SingularAttribute<GovComplianceCheck, Instant> checkedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceCheck#id
	 **/
	public static volatile SingularAttribute<GovComplianceCheck, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceCheck#detail
	 **/
	public static volatile SingularAttribute<GovComplianceCheck, String> detail;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceCheck#ruleId
	 **/
	public static volatile SingularAttribute<GovComplianceCheck, UUID> ruleId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceCheck
	 **/
	public static volatile EntityType<GovComplianceCheck> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovComplianceCheck#status
	 **/
	public static volatile SingularAttribute<GovComplianceCheck, String> status;

}

