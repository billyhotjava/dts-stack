package com.yuzhi.dts.platform.domain.service;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(SvcDataProduct.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SvcDataProduct_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SUBSCRIPTIONS = "subscriptions";
	public static final String CODE = "code";
	public static final String SLA = "sla";
	public static final String DESCRIPTION = "description";
	public static final String REFRESH_FREQUENCY = "refreshFrequency";
	public static final String CLASSIFICATION = "classification";
	public static final String CURRENT_VERSION = "currentVersion";
	public static final String NAME = "name";
	public static final String LATENCY_OBJECTIVE = "latencyObjective";
	public static final String ID = "id";
	public static final String FAILURE_POLICY = "failurePolicy";
	public static final String PRODUCT_TYPE = "productType";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProduct#subscriptions
	 **/
	public static volatile SingularAttribute<SvcDataProduct, Integer> subscriptions;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProduct#code
	 **/
	public static volatile SingularAttribute<SvcDataProduct, String> code;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProduct#sla
	 **/
	public static volatile SingularAttribute<SvcDataProduct, String> sla;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProduct#description
	 **/
	public static volatile SingularAttribute<SvcDataProduct, String> description;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProduct#refreshFrequency
	 **/
	public static volatile SingularAttribute<SvcDataProduct, String> refreshFrequency;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProduct#classification
	 **/
	public static volatile SingularAttribute<SvcDataProduct, String> classification;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProduct#currentVersion
	 **/
	public static volatile SingularAttribute<SvcDataProduct, String> currentVersion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProduct#name
	 **/
	public static volatile SingularAttribute<SvcDataProduct, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProduct#latencyObjective
	 **/
	public static volatile SingularAttribute<SvcDataProduct, String> latencyObjective;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProduct#id
	 **/
	public static volatile SingularAttribute<SvcDataProduct, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProduct
	 **/
	public static volatile EntityType<SvcDataProduct> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProduct#failurePolicy
	 **/
	public static volatile SingularAttribute<SvcDataProduct, String> failurePolicy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProduct#productType
	 **/
	public static volatile SingularAttribute<SvcDataProduct, String> productType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProduct#status
	 **/
	public static volatile SingularAttribute<SvcDataProduct, String> status;

}

