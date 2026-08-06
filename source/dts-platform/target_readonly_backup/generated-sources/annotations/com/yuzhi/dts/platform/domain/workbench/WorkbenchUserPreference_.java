package com.yuzhi.dts.platform.domain.workbench;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(WorkbenchUserPreference.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class WorkbenchUserPreference_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String LAYOUT_JSON = "layoutJson";
	public static final String ID = "id";
	public static final String VERSION = "version";
	public static final String USERNAME = "username";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.workbench.WorkbenchUserPreference#layoutJson
	 **/
	public static volatile SingularAttribute<WorkbenchUserPreference, String> layoutJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.workbench.WorkbenchUserPreference#id
	 **/
	public static volatile SingularAttribute<WorkbenchUserPreference, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.workbench.WorkbenchUserPreference
	 **/
	public static volatile EntityType<WorkbenchUserPreference> class_;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.workbench.WorkbenchUserPreference#version
	 **/
	public static volatile SingularAttribute<WorkbenchUserPreference, Integer> version;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.workbench.WorkbenchUserPreference#username
	 **/
	public static volatile SingularAttribute<WorkbenchUserPreference, String> username;

}

