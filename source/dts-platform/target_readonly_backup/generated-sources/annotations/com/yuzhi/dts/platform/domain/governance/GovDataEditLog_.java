package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(GovDataEditLog.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovDataEditLog_ {

	public static final String NEW_VALUE = "newValue";
	public static final String EDIT_REASON = "editReason";
	public static final String EDITED_BY = "editedBy";
	public static final String EDITED_AT = "editedAt";
	public static final String EDIT_TYPE = "editType";
	public static final String ID = "id";
	public static final String OLD_VALUE = "oldValue";
	public static final String TABLE_NAME = "tableName";
	public static final String ROW_ID = "rowId";
	public static final String COLUMN_NAME = "columnName";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDataEditLog#newValue
	 **/
	public static volatile SingularAttribute<GovDataEditLog, String> newValue;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDataEditLog#editReason
	 **/
	public static volatile SingularAttribute<GovDataEditLog, String> editReason;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDataEditLog#editedBy
	 **/
	public static volatile SingularAttribute<GovDataEditLog, String> editedBy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDataEditLog#editedAt
	 **/
	public static volatile SingularAttribute<GovDataEditLog, Instant> editedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDataEditLog#editType
	 **/
	public static volatile SingularAttribute<GovDataEditLog, String> editType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDataEditLog#id
	 **/
	public static volatile SingularAttribute<GovDataEditLog, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDataEditLog#oldValue
	 **/
	public static volatile SingularAttribute<GovDataEditLog, String> oldValue;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDataEditLog
	 **/
	public static volatile EntityType<GovDataEditLog> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDataEditLog#tableName
	 **/
	public static volatile SingularAttribute<GovDataEditLog, String> tableName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDataEditLog#rowId
	 **/
	public static volatile SingularAttribute<GovDataEditLog, String> rowId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDataEditLog#columnName
	 **/
	public static volatile SingularAttribute<GovDataEditLog, String> columnName;

}

