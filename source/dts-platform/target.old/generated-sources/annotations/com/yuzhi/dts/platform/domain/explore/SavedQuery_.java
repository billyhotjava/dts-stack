package com.yuzhi.dts.platform.domain.explore;

import com.yuzhi.dts.platform.domain.explore.ExecEnums.ExecEngine;
import com.yuzhi.dts.platform.domain.explore.ExecEnums.SecurityLevel;
import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(SavedQuery.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SavedQuery_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SQL_TEXT = "sqlText";
	public static final String ENGINE = "engine";
	public static final String LEVEL = "level";
	public static final String CONNECTION = "connection";
	public static final String ID = "id";
	public static final String TITLE = "title";
	public static final String TAGS = "tags";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SavedQuery#sqlText
	 **/
	public static volatile SingularAttribute<SavedQuery, String> sqlText;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SavedQuery#engine
	 **/
	public static volatile SingularAttribute<SavedQuery, ExecEngine> engine;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SavedQuery#level
	 **/
	public static volatile SingularAttribute<SavedQuery, SecurityLevel> level;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SavedQuery#connection
	 **/
	public static volatile SingularAttribute<SavedQuery, String> connection;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SavedQuery#id
	 **/
	public static volatile SingularAttribute<SavedQuery, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SavedQuery#title
	 **/
	public static volatile SingularAttribute<SavedQuery, String> title;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SavedQuery
	 **/
	public static volatile EntityType<SavedQuery> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.explore.SavedQuery#tags
	 **/
	public static volatile SingularAttribute<SavedQuery, String> tags;

}

