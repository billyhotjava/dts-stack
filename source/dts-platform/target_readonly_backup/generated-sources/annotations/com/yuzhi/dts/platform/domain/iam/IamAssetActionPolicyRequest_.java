package com.yuzhi.dts.platform.domain.iam;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(IamAssetActionPolicyRequest.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class IamAssetActionPolicyRequest_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String REASON = "reason";
	public static final String RESOURCE_ID = "resourceId";
	public static final String CHANGES_JSON = "changesJson";
	public static final String RESOURCE_NAME = "resourceName";
	public static final String VALID_FROM = "validFrom";
	public static final String DECIDED_BY = "decidedBy";
	public static final String SUBJECT_TYPE = "subjectType";
	public static final String SUBJECT_ID = "subjectId";
	public static final String REQUESTED_BY = "requestedBy";
	public static final String DECISION_NOTES = "decisionNotes";
	public static final String DECIDED_AT = "decidedAt";
	public static final String ID = "id";
	public static final String BEFORE_SNAPSHOT_JSON = "beforeSnapshotJson";
	public static final String SUBJECT_NAME = "subjectName";
	public static final String RESOURCE_TYPE = "resourceType";
	public static final String VALID_TO = "validTo";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#reason
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, String> reason;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#resourceId
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, String> resourceId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#changesJson
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, String> changesJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#resourceName
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, String> resourceName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#validFrom
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, Instant> validFrom;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#decidedBy
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, String> decidedBy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#subjectType
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, String> subjectType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#subjectId
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, String> subjectId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#requestedBy
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, String> requestedBy;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#decisionNotes
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, String> decisionNotes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#decidedAt
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, Instant> decidedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#id
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest
	 **/
	public static volatile EntityType<IamAssetActionPolicyRequest> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#beforeSnapshotJson
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, String> beforeSnapshotJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#subjectName
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, String> subjectName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#resourceType
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, String> resourceType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#validTo
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, Instant> validTo;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest#status
	 **/
	public static volatile SingularAttribute<IamAssetActionPolicyRequest, String> status;

}

