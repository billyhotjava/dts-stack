package com.yuzhi.dts.platform.domain.explore;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(ExploreSavedQuery.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class ExploreSavedQuery_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SQL_TEXT = "sqlText";
	public static final String NAME = "name";
	public static final String DATASET_ID = "datasetId";
	public static final String ID = "id";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ExploreSavedQuery#sqlText
	 **/
	public static volatile SingularAttribute<ExploreSavedQuery, String> sqlText;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ExploreSavedQuery#name
	 **/
	public static volatile SingularAttribute<ExploreSavedQuery, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ExploreSavedQuery#datasetId
	 **/
	public static volatile SingularAttribute<ExploreSavedQuery, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ExploreSavedQuery#id
	 **/
	public static volatile SingularAttribute<ExploreSavedQuery, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.ExploreSavedQuery
	 **/
	public static volatile EntityType<ExploreSavedQuery> class_;

}

