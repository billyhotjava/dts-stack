package com.yuzhi.dts.platform.domain.visualization;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(BiReportLink.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class BiReportLink_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String CODE = "code";
	public static final String DEPT_CODES = "deptCodes";
	public static final String QUERY_DATASET_ID = "queryDatasetId";
	public static final String BIZ_DOMAIN = "bizDomain";
	public static final String ROLE_CODES = "roleCodes";
	public static final String TITLE = "title";
	public static final String CLASSIFICATION = "classification";
	public static final String URL = "url";
	public static final String ENABLED = "enabled";
	public static final String EXPIRES_AT = "expiresAt";
	public static final String REPORT_TYPE = "reportType";
	public static final String QUERY_DATASET_VERSION = "queryDatasetVersion";
	public static final String ENGINE = "engine";
	public static final String SORT_ORDER = "sortOrder";
	public static final String LAST_VISITED_AT = "lastVisitedAt";
	public static final String ID = "id";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#code
	 **/
	public static volatile SingularAttribute<BiReportLink, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#deptCodes
	 **/
	public static volatile SingularAttribute<BiReportLink, String> deptCodes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#queryDatasetId
	 **/
	public static volatile SingularAttribute<BiReportLink, UUID> queryDatasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#bizDomain
	 **/
	public static volatile SingularAttribute<BiReportLink, String> bizDomain;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#roleCodes
	 **/
	public static volatile SingularAttribute<BiReportLink, String> roleCodes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#title
	 **/
	public static volatile SingularAttribute<BiReportLink, String> title;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#classification
	 **/
	public static volatile SingularAttribute<BiReportLink, String> classification;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#url
	 **/
	public static volatile SingularAttribute<BiReportLink, String> url;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#enabled
	 **/
	public static volatile SingularAttribute<BiReportLink, Boolean> enabled;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#expiresAt
	 **/
	public static volatile SingularAttribute<BiReportLink, Instant> expiresAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#reportType
	 **/
	public static volatile SingularAttribute<BiReportLink, String> reportType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#queryDatasetVersion
	 **/
	public static volatile SingularAttribute<BiReportLink, Integer> queryDatasetVersion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#engine
	 **/
	public static volatile SingularAttribute<BiReportLink, String> engine;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#sortOrder
	 **/
	public static volatile SingularAttribute<BiReportLink, Integer> sortOrder;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#lastVisitedAt
	 **/
	public static volatile SingularAttribute<BiReportLink, Instant> lastVisitedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink#id
	 **/
	public static volatile SingularAttribute<BiReportLink, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.visualization.BiReportLink
	 **/
	public static volatile EntityType<BiReportLink> class_;

}

