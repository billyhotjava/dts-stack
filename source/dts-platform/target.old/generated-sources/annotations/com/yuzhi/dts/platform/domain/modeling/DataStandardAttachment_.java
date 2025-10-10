package com.yuzhi.dts.platform.domain.modeling;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(DataStandardAttachment.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class DataStandardAttachment_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String STANDARD = "standard";
	public static final String FILE_NAME = "fileName";
	public static final String SHA256 = "sha256";
	public static final String KEY_VERSION = "keyVersion";
	public static final String FILE_SIZE = "fileSize";
	public static final String ID = "id";
	public static final String CIPHER_BLOB = "cipherBlob";
	public static final String VERSION = "version";
	public static final String CONTENT_TYPE = "contentType";
	public static final String IV = "iv";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardAttachment#standard
	 **/
	public static volatile SingularAttribute<DataStandardAttachment, DataStandard> standard;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardAttachment#fileName
	 **/
	public static volatile SingularAttribute<DataStandardAttachment, String> fileName;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardAttachment#sha256
	 **/
	public static volatile SingularAttribute<DataStandardAttachment, String> sha256;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardAttachment#keyVersion
	 **/
	public static volatile SingularAttribute<DataStandardAttachment, String> keyVersion;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardAttachment#fileSize
	 **/
	public static volatile SingularAttribute<DataStandardAttachment, Long> fileSize;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardAttachment#id
	 **/
	public static volatile SingularAttribute<DataStandardAttachment, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardAttachment#cipherBlob
	 **/
	public static volatile SingularAttribute<DataStandardAttachment, byte[]> cipherBlob;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardAttachment
	 **/
	public static volatile EntityType<DataStandardAttachment> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardAttachment#version
	 **/
	public static volatile SingularAttribute<DataStandardAttachment, String> version;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardAttachment#contentType
	 **/
	public static volatile SingularAttribute<DataStandardAttachment, String> contentType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.modeling.DataStandardAttachment#iv
	 **/
	public static volatile SingularAttribute<DataStandardAttachment, byte[]> iv;

}

