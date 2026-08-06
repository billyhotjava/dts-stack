package com.yuzhi.dts.platform.domain.service;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(SvcDataProductDataset.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class SvcDataProductDataset_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String PRODUCT_ID = "productId";
	public static final String DATASET_NAME = "datasetName";
	public static final String DATASET_ID = "datasetId";
	public static final String ID = "id";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductDataset#productId
	 **/
	public static volatile SingularAttribute<SvcDataProductDataset, UUID> productId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductDataset#datasetName
	 **/
	public static volatile SingularAttribute<SvcDataProductDataset, String> datasetName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductDataset#datasetId
	 **/
	public static volatile SingularAttribute<SvcDataProductDataset, UUID> datasetId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductDataset#id
	 **/
	public static volatile SingularAttribute<SvcDataProductDataset, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.service.SvcDataProductDataset
	 **/
	public static volatile EntityType<SvcDataProductDataset> class_;

}

