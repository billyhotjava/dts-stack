package com.yuzhi.dts.platform.domain.infra;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.time.Instant;
import java.util.UUID;

@StaticMetamodel(InfraExternalExchangeFile.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class InfraExternalExchangeFile_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String FILE_NAME = "fileName";
	public static final String DIRECTORY_ARTIFACT_ID = "directoryArtifactId";
	public static final String SOURCE_SYSTEM = "sourceSystem";
	public static final String FILE_PATH = "filePath";
	public static final String BATCH_CODE = "batchCode";
	public static final String ERROR_MESSAGE = "errorMessage";
	public static final String RECEIVED_AT = "receivedAt";
	public static final String CLASSIFICATION = "classification";
	public static final String EXTERNAL_REF = "externalRef";
	public static final String ENABLED = "enabled";
	public static final String PROPS = "props";
	public static final String FILE_SIZE = "fileSize";
	public static final String CHECKSUM = "checksum";
	public static final String PROCESSED_AT = "processedAt";
	public static final String ENTRY_KEY = "entryKey";
	public static final String ID = "id";
	public static final String OWNER_DEPT = "ownerDept";
	public static final String STATUS = "status";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#fileName
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, String> fileName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#directoryArtifactId
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, UUID> directoryArtifactId;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#sourceSystem
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, String> sourceSystem;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#filePath
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, String> filePath;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#batchCode
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, String> batchCode;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#errorMessage
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, String> errorMessage;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#receivedAt
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, Instant> receivedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#classification
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, String> classification;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#externalRef
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, String> externalRef;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#enabled
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, Boolean> enabled;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#props
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, String> props;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#fileSize
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, Long> fileSize;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#checksum
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, String> checksum;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#processedAt
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, Instant> processedAt;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#entryKey
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, String> entryKey;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#id
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile
	 **/
	public static volatile EntityType<InfraExternalExchangeFile> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#ownerDept
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, String> ownerDept;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile#status
	 **/
	public static volatile SingularAttribute<InfraExternalExchangeFile, String> status;

}

