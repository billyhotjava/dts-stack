package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(GovDimensionItem.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovDimensionItem_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String PARENT = "parent";
	public static final String ATTRIBUTES_JSON = "attributesJson";
	public static final String CODE = "code";
	public static final String NOTES = "notes";
	public static final String SORT_ORDER = "sortOrder";
	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String DIMENSION = "dimension";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionItem#parent
	 **/
	public static volatile SingularAttribute<GovDimensionItem, GovDimensionItem> parent;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionItem#attributesJson
	 **/
	public static volatile SingularAttribute<GovDimensionItem, String> attributesJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionItem#code
	 **/
	public static volatile SingularAttribute<GovDimensionItem, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionItem#notes
	 **/
	public static volatile SingularAttribute<GovDimensionItem, String> notes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionItem#sortOrder
	 **/
	public static volatile SingularAttribute<GovDimensionItem, Integer> sortOrder;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionItem#name
	 **/
	public static volatile SingularAttribute<GovDimensionItem, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionItem#id
	 **/
	public static volatile SingularAttribute<GovDimensionItem, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionItem
	 **/
	public static volatile EntityType<GovDimensionItem> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionItem#dimension
	 **/
	public static volatile SingularAttribute<GovDimensionItem, GovDimensionDictionary> dimension;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovDimensionItem#status
	 **/
	public static volatile SingularAttribute<GovDimensionItem, String> status;

}

