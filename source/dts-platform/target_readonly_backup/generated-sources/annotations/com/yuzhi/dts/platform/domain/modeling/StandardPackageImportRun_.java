package com.yuzhi.dts.platform.domain.modeling;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(StandardPackageImportRun.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class StandardPackageImportRun_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String SUMMARY = "summary";
	public static final String PAYLOAD_JSON = "payloadJson";
	public static final String PREVIEW_JSON = "previewJson";
	public static final String ID = "id";
	public static final String PACKAGE_NAME = "packageName";
	public static final String SOURCE = "source";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun#summary
	 **/
	public static volatile SingularAttribute<StandardPackageImportRun, String> summary;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun#payloadJson
	 **/
	public static volatile SingularAttribute<StandardPackageImportRun, String> payloadJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun#previewJson
	 **/
	public static volatile SingularAttribute<StandardPackageImportRun, String> previewJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun#id
	 **/
	public static volatile SingularAttribute<StandardPackageImportRun, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun#packageName
	 **/
	public static volatile SingularAttribute<StandardPackageImportRun, String> packageName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun#source
	 **/
	public static volatile SingularAttribute<StandardPackageImportRun, String> source;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun
	 **/
	public static volatile EntityType<StandardPackageImportRun> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun#status
	 **/
	public static volatile SingularAttribute<StandardPackageImportRun, String> status;

}

