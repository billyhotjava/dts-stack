package com.yuzhi.dts.admin.service.auditv2;

public final class ButtonCodes {
    private ButtonCodes() {}

    // System configuration
    public static final String SYSTEM_CONFIG_VIEW = "ADMIN_SYSTEM_CONFIG_VIEW";
    public static final String SYSTEM_CONFIG_SUBMIT = "ADMIN_SYSTEM_CONFIG_SUBMIT";
    public static final String DATA_SOURCE_CREATE = "ADMIN_DATA_SOURCE_CREATE";
    public static final String DATA_SOURCE_UPDATE = "ADMIN_DATA_SOURCE_UPDATE";
    public static final String DATA_SOURCE_REFRESH = "ADMIN_DATA_SOURCE_REFRESH";
    public static final String DATA_SOURCE_PUBLISH = "ADMIN_DATA_SOURCE_PUBLISH";
    public static final String DATA_SOURCE_TEST = "ADMIN_DATA_SOURCE_TEST";
    public static final String DATA_SOURCE_UPLOAD_KRB5 = "ADMIN_DATA_SOURCE_UPLOAD_KRB5";
    public static final String DATA_SOURCE_UPLOAD_KEYTAB = "ADMIN_DATA_SOURCE_UPLOAD_KEYTAB";
    public static final String DATA_SOURCE_DELETE = "ADMIN_DATA_SOURCE_DELETE";

    // Change request lifecycle
    public static final String CHANGE_REQUEST_SUBMIT = "ADMIN_CHANGE_REQUEST_SUBMIT";
    public static final String CHANGE_REQUEST_APPROVE = "ADMIN_CHANGE_REQUEST_APPROVE";
    public static final String CHANGE_REQUEST_REJECT = "ADMIN_CHANGE_REQUEST_REJECT";

    // User management
    public static final String USER_LIST = "ADMIN_USER_LIST";
    public static final String USER_SEARCH = "ADMIN_USER_SEARCH";
    public static final String USER_VIEW = "ADMIN_USER_VIEW";
    public static final String USER_CREATE = "ADMIN_USER_CREATE";
    public static final String USER_UPDATE = "ADMIN_USER_UPDATE";
    public static final String USER_RESET_PASSWORD = "ADMIN_USER_RESET_PASSWORD";
    public static final String USER_ENABLE = "ADMIN_USER_ENABLE";
    public static final String USER_DISABLE = "ADMIN_USER_DISABLE";
    public static final String USER_SET_PERSON_LEVEL = "ADMIN_USER_SET_PERSON_LEVEL";
    public static final String USER_ASSIGN_ROLES = "ADMIN_USER_ASSIGN_ROLES";
    public static final String USER_REMOVE_ROLES = "ADMIN_USER_REMOVE_ROLES";
    public static final String USER_GROUP_MEMBERSHIPS_VIEW = "ADMIN_USER_GROUP_MEMBERSHIPS_VIEW";

    // Portal menu management
    public static final String PORTAL_MENU_VIEW = "ADMIN_PORTAL_MENU_VIEW";
    public static final String PORTAL_MENU_CREATE = "ADMIN_PORTAL_MENU_CREATE";
    public static final String PORTAL_MENU_UPDATE = "ADMIN_PORTAL_MENU_UPDATE";
    public static final String PORTAL_MENU_DELETE = "ADMIN_PORTAL_MENU_DELETE";

    // Organization management
    public static final String ORG_CREATE = "ADMIN_ORG_CREATE";
    public static final String ORG_UPDATE = "ADMIN_ORG_UPDATE";
    public static final String ORG_DELETE = "ADMIN_ORG_DELETE";
    public static final String ORG_SYNC = "ADMIN_ORG_SYNC";
    public static final String ORG_LIST = "ADMIN_ORG_LIST";

    // Dataset management
    public static final String DATASET_LIST = "ADMIN_DATASET_LIST";

    // Role assignment management
    public static final String ROLE_ASSIGNMENT_LIST = "ADMIN_ROLE_ASSIGNMENT_LIST";
    public static final String ROLE_ASSIGNMENT_CREATE = "ADMIN_ROLE_ASSIGNMENT_CREATE";

    // Custom role management
    public static final String CUSTOM_ROLE_LIST = "ADMIN_CUSTOM_ROLE_LIST";
    public static final String CUSTOM_ROLE_CREATE = "ADMIN_CUSTOM_ROLE_CREATE";

    // Permission catalog
    public static final String PERMISSION_CATALOG_VIEW = "ADMIN_PERMISSION_CATALOG_VIEW";

    // Change request management
    public static final String CHANGE_REQUEST_LIST = "ADMIN_CHANGE_REQUEST_LIST";
    public static final String CHANGE_REQUEST_LIST_MINE = "ADMIN_CHANGE_REQUEST_LIST_MINE";
    public static final String CHANGE_REQUEST_VIEW = "ADMIN_CHANGE_REQUEST_VIEW";
    public static final String CHANGE_REQUEST_DRAFT = "ADMIN_CHANGE_REQUEST_DRAFT";
    public static final String CHANGE_REQUEST_PURGE = "ADMIN_CHANGE_REQUEST_PURGE";

    // Group management
    public static final String GROUP_LIST = "ADMIN_GROUP_LIST";
    public static final String GROUP_VIEW = "ADMIN_GROUP_VIEW";
    public static final String GROUP_CREATE = "ADMIN_GROUP_CREATE";
    public static final String GROUP_UPDATE = "ADMIN_GROUP_UPDATE";
    public static final String GROUP_DELETE = "ADMIN_GROUP_DELETE";
    public static final String GROUP_MEMBERS_VIEW = "ADMIN_GROUP_MEMBERS_VIEW";
    public static final String GROUP_USER_MEMBERS_VIEW = "ADMIN_GROUP_USER_MEMBERS_VIEW";
    public static final String GROUP_ADD_MEMBER = "ADMIN_GROUP_ADD_MEMBER";
    public static final String GROUP_REMOVE_MEMBER = "ADMIN_GROUP_REMOVE_MEMBER";

    // Role management
    public static final String ROLE_PLATFORM_LIST = "ADMIN_ROLE_PLATFORM_LIST";
    public static final String ROLE_LIST = "ADMIN_ROLE_LIST";
    public static final String ROLE_VIEW = "ADMIN_ROLE_VIEW";
    public static final String ROLE_USERS_VIEW = "ADMIN_ROLE_USERS_VIEW";
    public static final String ROLE_CREATE = "ADMIN_ROLE_CREATE";
    public static final String ROLE_UPDATE = "ADMIN_ROLE_UPDATE";
    public static final String ROLE_DELETE = "ADMIN_ROLE_DELETE";

    // Approval management
    public static final String APPROVAL_LIST = "ADMIN_APPROVAL_LIST";
    public static final String APPROVAL_VIEW = "ADMIN_APPROVAL_VIEW";
    public static final String APPROVAL_APPROVE = "ADMIN_APPROVAL_APPROVE";
    public static final String APPROVAL_REJECT = "ADMIN_APPROVAL_REJECT";
    public static final String APPROVAL_DELAY = "ADMIN_APPROVAL_DELAY";

    // Authentication
    public static final String AUTH_ADMIN_LOGIN = "ADMIN_AUTH_LOGIN";
    public static final String AUTH_ADMIN_LOGOUT = "ADMIN_AUTH_LOGOUT";
    public static final String AUTH_ADMIN_REFRESH = "ADMIN_AUTH_REFRESH";
    public static final String AUTH_PLATFORM_LOGIN = "ADMIN_AUTH_PLATFORM_LOGIN";

    // Audit log management
    public static final String AUDIT_LOG_QUERY = "ADMIN_AUDIT_LOG_QUERY";
    public static final String AUDIT_LOG_EXPORT = "ADMIN_AUDIT_LOG_EXPORT";

    // Platform ingestion fallback
    public static final String PLATFORM_GENERIC_EVENT = "PLATFORM_GENERIC_EVENT";

    // Master data
    public static final String MASTERDATA_PERSON_IMPORT_API = "ADMIN_PERSON_IMPORT_API";
    public static final String MASTERDATA_PERSON_IMPORT_EXCEL = "ADMIN_PERSON_IMPORT_EXCEL";
    public static final String MASTERDATA_PERSON_IMPORT_MANUAL = "ADMIN_PERSON_IMPORT_MANUAL";
}
