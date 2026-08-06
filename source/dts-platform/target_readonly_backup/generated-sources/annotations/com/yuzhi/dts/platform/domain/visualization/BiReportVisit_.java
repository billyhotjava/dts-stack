package com.yuzhi.dts.platform.domain.visualization;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(BiReportVisit.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class BiReportVisit_ {

	public static final String USER_LOGIN = "userLogin";
	public static final String REPORT_ID = "reportId";
	public static final String BIZ_DOMAIN = "bizDomain";
	public static final String ID = "id";
	public static final String DEPT_CODE = "deptCode";
	public static final String VISITED_AT = "visitedAt";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportVisit#userLogin
	 **/
	public static volatile SingularAttribute<BiReportVisit, String> userLogin;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportVisit#reportId
	 **/
	public static volatile SingularAttribute<BiReportVisit, UUID> reportId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportVisit#bizDomain
	 **/
	public static volatile SingularAttribute<BiReportVisit, String> bizDomain;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportVisit#id
	 **/
	public static volatile SingularAttribute<BiReportVisit, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportVisit
	 **/
	public static volatile EntityType<BiReportVisit> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportVisit#deptCode
	 **/
	public static volatile SingularAttribute<BiReportVisit, String> deptCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportVisit#visitedAt
	 **/
	public static volatile SingularAttribute<BiReportVisit, Instant> visitedAt;

}

