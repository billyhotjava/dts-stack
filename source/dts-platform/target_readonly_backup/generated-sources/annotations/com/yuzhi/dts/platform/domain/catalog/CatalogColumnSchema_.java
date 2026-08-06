package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(CatalogColumnSchema.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogColumnSchema_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SENSITIVE_TAGS = "sensitiveTags";
	public static final String NULLABLE = "nullable";
	public static final String STANDARD_MISMATCH_REASON = "standardMismatchReason";
	public static final String DATA_TYPE = "dataType";
	public static final String STANDARD_ID = "standardId";
	public static final String TAGS = "tags";
	public static final String STANDARD_RULE = "standardRule";
	public static final String NAME = "name";
	public static final String COMMENT = "comment";
	public static final String ID = "id";
	public static final String TABLE = "table";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema#sensitiveTags
	 **/
	public static volatile SingularAttribute<CatalogColumnSchema, String> sensitiveTags;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema#nullable
	 **/
	public static volatile SingularAttribute<CatalogColumnSchema, Boolean> nullable;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema#standardMismatchReason
	 **/
	public static volatile SingularAttribute<CatalogColumnSchema, String> standardMismatchReason;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema#dataType
	 **/
	public static volatile SingularAttribute<CatalogColumnSchema, String> dataType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema#standardId
	 **/
	public static volatile SingularAttribute<CatalogColumnSchema, UUID> standardId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema#tags
	 **/
	public static volatile SingularAttribute<CatalogColumnSchema, String> tags;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema#standardRule
	 **/
	public static volatile SingularAttribute<CatalogColumnSchema, String> standardRule;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema#name
	 **/
	public static volatile SingularAttribute<CatalogColumnSchema, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema#comment
	 **/
	public static volatile SingularAttribute<CatalogColumnSchema, String> comment;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema#id
	 **/
	public static volatile SingularAttribute<CatalogColumnSchema, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema
	 **/
	public static volatile EntityType<CatalogColumnSchema> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema#table
	 **/
	public static volatile SingularAttribute<CatalogColumnSchema, CatalogTableSchema> table;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema#status
	 **/
	public static volatile SingularAttribute<CatalogColumnSchema, String> status;

}

