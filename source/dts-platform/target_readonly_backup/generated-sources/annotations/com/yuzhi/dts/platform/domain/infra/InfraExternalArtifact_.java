package com.yuzhi.dts.platform.domain.infra;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(InfraExternalArtifact.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class InfraExternalArtifact_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String LAST_SEEN_AT = "lastSeenAt";
	public static final String EXTERNAL_URL = "externalUrl";
	public static final String ARTIFACT_TYPE = "artifactType";
	public static final String LAST_MESSAGE = "lastMessage";
	public static final String EXTERNAL_ID = "externalId";
	public static final String CLASSIFICATION = "classification";
	public static final String ENABLED = "enabled";
	public static final String TAGS = "tags";
	public static final String PROPS = "props";
	public static final String NAME = "name";
	public static final String ENTRY_KEY = "entryKey";
	public static final String ID = "id";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact#lastSeenAt
	 **/
	public static volatile SingularAttribute<InfraExternalArtifact, Instant> lastSeenAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact#externalUrl
	 **/
	public static volatile SingularAttribute<InfraExternalArtifact, String> externalUrl;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact#artifactType
	 **/
	public static volatile SingularAttribute<InfraExternalArtifact, String> artifactType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact#lastMessage
	 **/
	public static volatile SingularAttribute<InfraExternalArtifact, String> lastMessage;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact#externalId
	 **/
	public static volatile SingularAttribute<InfraExternalArtifact, String> externalId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact#classification
	 **/
	public static volatile SingularAttribute<InfraExternalArtifact, String> classification;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact#enabled
	 **/
	public static volatile SingularAttribute<InfraExternalArtifact, Boolean> enabled;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact#tags
	 **/
	public static volatile SingularAttribute<InfraExternalArtifact, String> tags;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact#props
	 **/
	public static volatile SingularAttribute<InfraExternalArtifact, String> props;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact#name
	 **/
	public static volatile SingularAttribute<InfraExternalArtifact, String> name;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact#entryKey
	 **/
	public static volatile SingularAttribute<InfraExternalArtifact, String> entryKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact#id
	 **/
	public static volatile SingularAttribute<InfraExternalArtifact, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact
	 **/
	public static volatile EntityType<InfraExternalArtifact> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact#ownerDept
	 **/
	public static volatile SingularAttribute<InfraExternalArtifact, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalArtifact#status
	 **/
	public static volatile SingularAttribute<InfraExternalArtifact, String> status;

}

