package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(GovReferenceImportRun.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovReferenceImportRun_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String CODE_TYPE_ID = "codeTypeId";
	public static final String SUMMARY = "summary";
	public static final String IMPORT_MODE = "importMode";
	public static final String CONFLICT_POLICY = "conflictPolicy";
	public static final String PREVIEW_JSON = "previewJson";
	public static final String AFTER_SNAPSHOT_JSON = "afterSnapshotJson";
	public static final String ID = "id";
	public static final String BEFORE_SNAPSHOT_JSON = "beforeSnapshotJson";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovReferenceImportRun#codeTypeId
	 **/
	public static volatile SingularAttribute<GovReferenceImportRun, String> codeTypeId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovReferenceImportRun#summary
	 **/
	public static volatile SingularAttribute<GovReferenceImportRun, String> summary;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovReferenceImportRun#importMode
	 **/
	public static volatile SingularAttribute<GovReferenceImportRun, String> importMode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovReferenceImportRun#conflictPolicy
	 **/
	public static volatile SingularAttribute<GovReferenceImportRun, String> conflictPolicy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovReferenceImportRun#previewJson
	 **/
	public static volatile SingularAttribute<GovReferenceImportRun, String> previewJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovReferenceImportRun#afterSnapshotJson
	 **/
	public static volatile SingularAttribute<GovReferenceImportRun, String> afterSnapshotJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovReferenceImportRun#id
	 **/
	public static volatile SingularAttribute<GovReferenceImportRun, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovReferenceImportRun
	 **/
	public static volatile EntityType<GovReferenceImportRun> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovReferenceImportRun#beforeSnapshotJson
	 **/
	public static volatile SingularAttribute<GovReferenceImportRun, String> beforeSnapshotJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovReferenceImportRun#status
	 **/
	public static volatile SingularAttribute<GovReferenceImportRun, String> status;

}

