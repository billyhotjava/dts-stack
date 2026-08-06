package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(GovIndicatorSubscription.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovIndicatorSubscription_ {

	public static final String USER_LOGIN = "userLogin";
	public static final String INDICATOR_ID = "indicatorId";
	public static final String CREATED_DATE = "createdDate";
	public static final String DISPLAY_ORDER = "displayOrder";
	public static final String ID = "id";
	public static final String FILTER_CONFIG = "filterConfig";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorSubscription#userLogin
	 **/
	public static volatile SingularAttribute<GovIndicatorSubscription, String> userLogin;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorSubscription#indicatorId
	 **/
	public static volatile SingularAttribute<GovIndicatorSubscription, UUID> indicatorId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorSubscription#createdDate
	 **/
	public static volatile SingularAttribute<GovIndicatorSubscription, Instant> createdDate;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorSubscription#displayOrder
	 **/
	public static volatile SingularAttribute<GovIndicatorSubscription, Integer> displayOrder;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorSubscription#id
	 **/
	public static volatile SingularAttribute<GovIndicatorSubscription, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorSubscription
	 **/
	public static volatile EntityType<GovIndicatorSubscription> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIndicatorSubscription#filterConfig
	 **/
	public static volatile SingularAttribute<GovIndicatorSubscription, String> filterConfig;

}

