package com.yuzhi.dts.platform.domain.explore;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(QueryDatasetAsset.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class QueryDatasetAsset_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SQL_TEXT = "sqlText";
	public static final String PARAMETERS_JSON = "parametersJson";
	public static final String DESCRIPTION = "description";
	public static final String LATEST_EXECUTION_ID = "latestExecutionId";
	public static final String ENABLED = "enabled";
	public static final String SOURCE_DATASOURCE_ID = "sourceDatasourceId";
	public static final String PUBLISHED_VERSION = "publishedVersion";
	public static final String LATEST_RESULT_SET_ID = "latestResultSetId";
	public static final String NAME = "name";
	public static final String SOURCE_DATASOURCE_NAME = "sourceDatasourceName";
	public static final String ID = "id";
	public static final String REFRESH_STRATEGY = "refreshStrategy";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset#sqlText
	 **/
	public static volatile SingularAttribute<QueryDatasetAsset, String> sqlText;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset#parametersJson
	 **/
	public static volatile SingularAttribute<QueryDatasetAsset, String> parametersJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset#description
	 **/
	public static volatile SingularAttribute<QueryDatasetAsset, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset#latestExecutionId
	 **/
	public static volatile SingularAttribute<QueryDatasetAsset, UUID> latestExecutionId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset#enabled
	 **/
	public static volatile SingularAttribute<QueryDatasetAsset, Boolean> enabled;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset#sourceDatasourceId
	 **/
	public static volatile SingularAttribute<QueryDatasetAsset, UUID> sourceDatasourceId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset#publishedVersion
	 **/
	public static volatile SingularAttribute<QueryDatasetAsset, Integer> publishedVersion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset#latestResultSetId
	 **/
	public static volatile SingularAttribute<QueryDatasetAsset, UUID> latestResultSetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset#name
	 **/
	public static volatile SingularAttribute<QueryDatasetAsset, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset#sourceDatasourceName
	 **/
	public static volatile SingularAttribute<QueryDatasetAsset, String> sourceDatasourceName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset#id
	 **/
	public static volatile SingularAttribute<QueryDatasetAsset, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset#refreshStrategy
	 **/
	public static volatile SingularAttribute<QueryDatasetAsset, String> refreshStrategy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset
	 **/
	public static volatile EntityType<QueryDatasetAsset> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset#ownerDept
	 **/
	public static volatile SingularAttribute<QueryDatasetAsset, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset#status
	 **/
	public static volatile SingularAttribute<QueryDatasetAsset, String> status;

}

