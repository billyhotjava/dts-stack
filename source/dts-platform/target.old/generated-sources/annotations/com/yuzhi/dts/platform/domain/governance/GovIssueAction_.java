package com.yuzhi.dts.platform.domain.governance;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(GovIssueAction.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class GovIssueAction_ extends com.yuzhi.dts.platform.domain.AbstractAuditingEntity_ {

	public static final String ACTOR = "actor";
	public static final String ACTION_TYPE = "actionType";
	public static final String ATTACHMENTS_JSON = "attachmentsJson";
	public static final String NOTES = "notes";
	public static final String TICKET = "ticket";
	public static final String ID = "id";

	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueAction#actor
	 **/
	public static volatile SingularAttribute<GovIssueAction, String> actor;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueAction#actionType
	 **/
	public static volatile SingularAttribute<GovIssueAction, String> actionType;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueAction#attachmentsJson
	 **/
	public static volatile SingularAttribute<GovIssueAction, String> attachmentsJson;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueAction#notes
	 **/
	public static volatile SingularAttribute<GovIssueAction, String> notes;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueAction#ticket
	 **/
	public static volatile SingularAttribute<GovIssueAction, GovIssueTicket> ticket;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueAction#id
	 **/
	public static volatile SingularAttribute<GovIssueAction, UUID> id;
	
	/**
	 * @see com.yuzhi.dts.platform.domain.governance.GovIssueAction
	 **/
	public static volatile EntityType<GovIssueAction> class_;

}

