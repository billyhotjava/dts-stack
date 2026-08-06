package com.yuzhi.dts.platform.domain.goldenchain;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(GoldenChainStageSnapshotRecord.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GoldenChainStageSnapshotRecord_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String OWNER = "owner";
	public static final String CHAIN_INSTANCE_ID = "chainInstanceId";
	public static final String BLOCKER_CODE = "blockerCode";
	public static final String SOURCE_REF_ID = "sourceRefId";
	public static final String STAGE_SEQUENCE = "stageSequence";
	public static final String BLOCKER_REASON = "blockerReason";
	public static final String STAGE = "stage";
	public static final String EVIDENCE_REF = "evidenceRef";
	public static final String EVIDENCE_TYPE = "evidenceType";
	public static final String SOURCE_REF_TYPE = "sourceRefType";
	public static final String ID = "id";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord#owner
	 **/
	public static volatile SingularAttribute<GoldenChainStageSnapshotRecord, String> owner;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord#chainInstanceId
	 **/
	public static volatile SingularAttribute<GoldenChainStageSnapshotRecord, UUID> chainInstanceId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord#blockerCode
	 **/
	public static volatile SingularAttribute<GoldenChainStageSnapshotRecord, GoldenChainBlockerCode> blockerCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord#sourceRefId
	 **/
	public static volatile SingularAttribute<GoldenChainStageSnapshotRecord, String> sourceRefId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord#stageSequence
	 **/
	public static volatile SingularAttribute<GoldenChainStageSnapshotRecord, Integer> stageSequence;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord#blockerReason
	 **/
	public static volatile SingularAttribute<GoldenChainStageSnapshotRecord, String> blockerReason;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord#stage
	 **/
	public static volatile SingularAttribute<GoldenChainStageSnapshotRecord, GoldenChainStage> stage;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord#evidenceRef
	 **/
	public static volatile SingularAttribute<GoldenChainStageSnapshotRecord, String> evidenceRef;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord#evidenceType
	 **/
	public static volatile SingularAttribute<GoldenChainStageSnapshotRecord, String> evidenceType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord#sourceRefType
	 **/
	public static volatile SingularAttribute<GoldenChainStageSnapshotRecord, String> sourceRefType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord#id
	 **/
	public static volatile SingularAttribute<GoldenChainStageSnapshotRecord, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord
	 **/
	public static volatile EntityType<GoldenChainStageSnapshotRecord> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.goldenchain.GoldenChainStageSnapshotRecord#status
	 **/
	public static volatile SingularAttribute<GoldenChainStageSnapshotRecord, GoldenChainStageStatus> status;

}

