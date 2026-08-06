package com.yuzhi.dts.platform.domain.catalog;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(CatalogAssetResolutionFailure.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CatalogAssetResolutionFailure_ {

	public static final String TYPE_HINT_GUESS = "typeHintGuess";
	public static final String REASON = "reason";
	public static final String REF = "ref";
	public static final String CALLER = "caller";
	public static final String REQUESTED_AT = "requestedAt";
	public static final String ID = "id";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure#typeHintGuess
	 **/
	public static volatile SingularAttribute<CatalogAssetResolutionFailure, String> typeHintGuess;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure#reason
	 **/
	public static volatile SingularAttribute<CatalogAssetResolutionFailure, String> reason;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure#ref
	 **/
	public static volatile SingularAttribute<CatalogAssetResolutionFailure, String> ref;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure#caller
	 **/
	public static volatile SingularAttribute<CatalogAssetResolutionFailure, String> caller;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure#requestedAt
	 **/
	public static volatile SingularAttribute<CatalogAssetResolutionFailure, Instant> requestedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure#id
	 **/
	public static volatile SingularAttribute<CatalogAssetResolutionFailure, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.catalog.CatalogAssetResolutionFailure
	 **/
	public static volatile EntityType<CatalogAssetResolutionFailure> class_;

}

