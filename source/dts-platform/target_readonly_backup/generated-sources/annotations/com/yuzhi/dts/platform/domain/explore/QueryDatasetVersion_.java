package com.yuzhi.dts.platform.domain.explore;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(QueryDatasetVersion.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class QueryDatasetVersion_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String EXECUTION_ID = "executionId";
	public static final String SQL_TEXT = "sqlText";
	public static final String PUBLISHED_AT = "publishedAt";
	public static final String VERSION_NO = "versionNo";
	public static final String ID = "id";
	public static final String DATASET = "dataset";
	public static final String CHANGE_SUMMARY = "changeSummary";
	public static final String RESULT_SET_ID = "resultSetId";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion#executionId
	 **/
	public static volatile SingularAttribute<QueryDatasetVersion, UUID> executionId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion#sqlText
	 **/
	public static volatile SingularAttribute<QueryDatasetVersion, String> sqlText;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion#publishedAt
	 **/
	public static volatile SingularAttribute<QueryDatasetVersion, Instant> publishedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion#versionNo
	 **/
	public static volatile SingularAttribute<QueryDatasetVersion, Integer> versionNo;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion#id
	 **/
	public static volatile SingularAttribute<QueryDatasetVersion, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion
	 **/
	public static volatile EntityType<QueryDatasetVersion> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion#dataset
	 **/
	public static volatile SingularAttribute<QueryDatasetVersion, QueryDatasetAsset> dataset;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion#changeSummary
	 **/
	public static volatile SingularAttribute<QueryDatasetVersion, String> changeSummary;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion#resultSetId
	 **/
	public static volatile SingularAttribute<QueryDatasetVersion, UUID> resultSetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion#status
	 **/
	public static volatile SingularAttribute<QueryDatasetVersion, String> status;

}

