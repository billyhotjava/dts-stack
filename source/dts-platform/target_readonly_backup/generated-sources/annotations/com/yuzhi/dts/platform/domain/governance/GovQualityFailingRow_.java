package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(GovQualityFailingRow.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovQualityFailingRow_ {

	public static final String CREATED_DATE = "createdDate";
	public static final String ACTUAL_VALUE = "actualValue";
	public static final String RUN = "run";
	public static final String FAIL_REASON = "failReason";
	public static final String ID = "id";
	public static final String RULE_ID = "ruleId";
	public static final String TABLE_NAME = "tableName";
	public static final String ROW_ID = "rowId";
	public static final String COLUMN_NAME = "columnName";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow#createdDate
	 **/
	public static volatile SingularAttribute<GovQualityFailingRow, Instant> createdDate;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow#actualValue
	 **/
	public static volatile SingularAttribute<GovQualityFailingRow, String> actualValue;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow#run
	 **/
	public static volatile SingularAttribute<GovQualityFailingRow, GovQualityRun> run;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow#failReason
	 **/
	public static volatile SingularAttribute<GovQualityFailingRow, String> failReason;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow#id
	 **/
	public static volatile SingularAttribute<GovQualityFailingRow, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow#ruleId
	 **/
	public static volatile SingularAttribute<GovQualityFailingRow, UUID> ruleId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow
	 **/
	public static volatile EntityType<GovQualityFailingRow> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow#tableName
	 **/
	public static volatile SingularAttribute<GovQualityFailingRow, String> tableName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow#rowId
	 **/
	public static volatile SingularAttribute<GovQualityFailingRow, String> rowId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow#columnName
	 **/
	public static volatile SingularAttribute<GovQualityFailingRow, String> columnName;

}

