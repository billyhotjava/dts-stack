package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(GovIndicatorDefinition.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovIndicatorDefinition_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String LLM_CONFIDENCE = "llmConfidence";
	public static final String PRECISION_SCALE = "precisionScale";
	public static final String DATA_PRIVACY = "dataPrivacy";
	public static final String NUMERATOR_EXPRESSION = "numeratorExpression";
	public static final String TEMPLATE_ID = "templateId";
	public static final String DENOMINATOR_EXPRESSION = "denominatorExpression";
	public static final String DYNAMIC_FILTER_CONFIG = "dynamicFilterConfig";
	public static final String LAST_VALIDATION_SIGNATURE = "lastValidationSignature";
	public static final String JOIN_CONFIG = "joinConfig";
	public static final String STATIC_FILTER = "staticFilter";
	public static final String DEPENDENCY_INDICATORS = "dependencyIndicators";
	public static final String HUMAN_VERIFIED = "humanVerified";
	public static final String SOURCE_LAYER = "sourceLayer";
	public static final String ID = "id";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String LLM_GENERATED = "llmGenerated";
	public static final String IS_DERIVED = "isDerived";
	public static final String TARGET_MODEL_NAME = "targetModelName";
	public static final String SOURCE_TABLE = "sourceTable";
	public static final String THRESHOLD_MAX = "thresholdMax";
	public static final String VERSION = "version";
	public static final String TAGS = "tags";
	public static final String UNIT = "unit";
	public static final String THRESHOLD_MIN = "thresholdMin";
	public static final String LAST_VALIDATED_AT = "lastValidatedAt";
	public static final String GRANULARITY = "granularity";
	public static final String DOMAIN = "domain";
	public static final String NAME = "name";
	public static final String WINDOW_FUNCTION = "windowFunction";
	public static final String VERSION_NOTES = "versionNotes";
	public static final String DATE_COLUMN = "dateColumn";
	public static final String DATA_LEVEL = "dataLevel";
	public static final String STATUS = "status";
	public static final String AGGREGATION_TYPE = "aggregationType";
	public static final String CODE = "code";
	public static final String ICON = "icon";
	public static final String DISPLAY_ORDER = "displayOrder";
	public static final String DATASET_ID = "datasetId";
	public static final String DEFINITION = "definition";
	public static final String EXPRESSION_SQL = "expressionSql";
	public static final String DIRECTION = "direction";
	public static final String OWNER = "owner";
	public static final String LLM_SOURCE_REF = "llmSourceRef";
	public static final String DIMENSION_FIELDS = "dimensionFields";
	public static final String TARGET_LAYER = "targetLayer";
	public static final String LAST_VALIDATION_STATUS = "lastValidationStatus";
	public static final String TIME_GRAIN = "timeGrain";
	public static final String LAST_VALIDATION_MESSAGE = "lastValidationMessage";
	public static final String MEASURE_FIELD = "measureField";
	public static final String CATEGORY = "category";
	public static final String BUSINESS_OWNER = "businessOwner";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#llmConfidence
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, Float> llmConfidence;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#precisionScale
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, Integer> precisionScale;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#dataPrivacy
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> dataPrivacy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#numeratorExpression
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> numeratorExpression;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#templateId
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, UUID> templateId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#denominatorExpression
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> denominatorExpression;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#dynamicFilterConfig
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> dynamicFilterConfig;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#lastValidationSignature
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> lastValidationSignature;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#joinConfig
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> joinConfig;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#staticFilter
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> staticFilter;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#dependencyIndicators
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> dependencyIndicators;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#humanVerified
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, Boolean> humanVerified;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#sourceLayer
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> sourceLayer;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#id
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#ownerDept
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#llmGenerated
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, Boolean> llmGenerated;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#isDerived
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, Boolean> isDerived;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#targetModelName
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> targetModelName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#sourceTable
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> sourceTable;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#thresholdMax
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, BigDecimal> thresholdMax;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#version
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> version;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#tags
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> tags;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#unit
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> unit;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#thresholdMin
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, BigDecimal> thresholdMin;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#lastValidatedAt
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, Instant> lastValidatedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#granularity
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> granularity;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#domain
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> domain;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#name
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#windowFunction
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> windowFunction;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#versionNotes
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> versionNotes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#dateColumn
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> dateColumn;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#dataLevel
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> dataLevel;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#status
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> status;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#aggregationType
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> aggregationType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#code
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#icon
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> icon;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#displayOrder
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, Integer> displayOrder;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#datasetId
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#definition
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> definition;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition
	 **/
	public static volatile EntityType<GovIndicatorDefinition> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#expressionSql
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> expressionSql;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#direction
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> direction;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#owner
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> owner;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#llmSourceRef
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> llmSourceRef;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#dimensionFields
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> dimensionFields;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#targetLayer
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> targetLayer;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#lastValidationStatus
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> lastValidationStatus;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#timeGrain
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> timeGrain;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#lastValidationMessage
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> lastValidationMessage;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#measureField
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> measureField;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#category
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> category;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition#businessOwner
	 **/
	public static volatile SingularAttribute<GovIndicatorDefinition, String> businessOwner;

}

