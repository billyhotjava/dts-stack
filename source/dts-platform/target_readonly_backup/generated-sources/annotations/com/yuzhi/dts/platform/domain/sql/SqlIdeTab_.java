package com.yuzhi.dts.platform.domain.sql;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(SqlIdeTab.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SqlIdeTab_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SQL_TEXT = "sqlText";
	public static final String SELECTION_JSON = "selectionJson";
	public static final String SCHEMA_CTX = "schemaCtx";
	public static final String ACTIVE = "active";
	public static final String TITLE = "title";
	public static final String USER_LOGIN = "userLogin";
	public static final String ENGINE = "engine";
	public static final String SORT_ORDER = "sortOrder";
	public static final String DATASOURCE_ID = "datasourceId";
	public static final String ID = "id";
	public static final String LAST_EXECUTION_ID = "lastExecutionId";
	public static final String CURSOR_LINE = "cursorLine";
	public static final String CURSOR_COL = "cursorCol";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.sql.SqlIdeTab#sqlText
	 **/
	public static volatile SingularAttribute<SqlIdeTab, String> sqlText;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.sql.SqlIdeTab#selectionJson
	 **/
	public static volatile SingularAttribute<SqlIdeTab, String> selectionJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.sql.SqlIdeTab#schemaCtx
	 **/
	public static volatile SingularAttribute<SqlIdeTab, String> schemaCtx;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.sql.SqlIdeTab#active
	 **/
	public static volatile SingularAttribute<SqlIdeTab, Boolean> active;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.sql.SqlIdeTab#title
	 **/
	public static volatile SingularAttribute<SqlIdeTab, String> title;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.sql.SqlIdeTab#userLogin
	 **/
	public static volatile SingularAttribute<SqlIdeTab, String> userLogin;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.sql.SqlIdeTab#engine
	 **/
	public static volatile SingularAttribute<SqlIdeTab, String> engine;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.sql.SqlIdeTab#sortOrder
	 **/
	public static volatile SingularAttribute<SqlIdeTab, Integer> sortOrder;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.sql.SqlIdeTab#datasourceId
	 **/
	public static volatile SingularAttribute<SqlIdeTab, UUID> datasourceId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.sql.SqlIdeTab#id
	 **/
	public static volatile SingularAttribute<SqlIdeTab, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.sql.SqlIdeTab#lastExecutionId
	 **/
	public static volatile SingularAttribute<SqlIdeTab, UUID> lastExecutionId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.sql.SqlIdeTab
	 **/
	public static volatile EntityType<SqlIdeTab> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.sql.SqlIdeTab#cursorLine
	 **/
	public static volatile SingularAttribute<SqlIdeTab, Integer> cursorLine;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.sql.SqlIdeTab#cursorCol
	 **/
	public static volatile SingularAttribute<SqlIdeTab, Integer> cursorCol;

}

