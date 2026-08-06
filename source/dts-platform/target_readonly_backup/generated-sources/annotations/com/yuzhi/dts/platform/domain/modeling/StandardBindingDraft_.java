package com.yuzhi.dts.platform.domain.modeling;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(StandardBindingDraft.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class StandardBindingDraft_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String FIELD_COUNT = "fieldCount";
	public static final String PAYLOAD_JSON = "payloadJson";
	public static final String ID = "id";
	public static final String SOURCE = "source";
	public static final String TITLE = "title";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardBindingDraft#fieldCount
	 **/
	public static volatile SingularAttribute<StandardBindingDraft, Integer> fieldCount;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardBindingDraft#payloadJson
	 **/
	public static volatile SingularAttribute<StandardBindingDraft, String> payloadJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardBindingDraft#id
	 **/
	public static volatile SingularAttribute<StandardBindingDraft, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardBindingDraft#source
	 **/
	public static volatile SingularAttribute<StandardBindingDraft, String> source;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardBindingDraft#title
	 **/
	public static volatile SingularAttribute<StandardBindingDraft, String> title;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardBindingDraft
	 **/
	public static volatile EntityType<StandardBindingDraft> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardBindingDraft#status
	 **/
	public static volatile SingularAttribute<StandardBindingDraft, String> status;

}

