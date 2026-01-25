export type HiveAuthMethod = "KEYTAB" | "PASSWORD" | "JDBC_PASSWORD";

export interface HiveConnectionTestRequest {
	jdbcUrl: string;
	loginPrincipal: string;
	krb5Conf?: string;
	authMethod: HiveAuthMethod;
	keytabBase64?: string;
	keytabFileName?: string;
	password?: string;
	jdbcProperties?: Record<string, string>;
	proxyUser?: string;
	testQuery?: string;
	remarks?: string;
	driverVersion?: string;
}

export interface HiveConnectionPersistRequest extends HiveConnectionTestRequest {
	name: string;
	description?: string;
	servicePrincipal?: string;
	host: string;
	port: number;
	database: string;
	useHttpTransport: boolean;
	httpPath?: string;
	useSsl: boolean;
	useCustomJdbc: boolean;
	customJdbcUrl?: string;
	lastTestElapsedMillis?: number;
	engineVersion?: string | null;
	driverVersion?: string | null;
	defaulted?: boolean;
	destinationId?: string;
	destinationDefinitionId?: string;
	destinationName?: string;
	destinationConfig?: Record<string, any>;
}

export interface HiveConnectionTestResult {
	success: boolean;
	message: string;
	elapsedMillis: number;
	engineVersion?: string | null;
	driverVersion?: string | null;
	warnings: string[];
}

export interface JdbcConnectionTestRequest {
	jdbcUrl?: string;
	driverClass?: string;
	driverVersion?: string;
	username?: string;
	password?: string;
	jdbcProperties?: Record<string, string>;
	testQuery?: string;
}

export interface InceptorConfig {
	id?: string;
	name?: string;
	description?: string;
	jdbcUrl?: string;
	loginPrincipal?: string;
	authMethod?: HiveAuthMethod | string;
	krb5Conf?: string;
	keytabBase64?: string;
	keytabFileName?: string;
	password?: string;
	jdbcProperties?: Record<string, string>;
	proxyUser?: string;
	servicePrincipal?: string;
	host?: string;
	port?: number;
	database?: string;
	useHttpTransport?: boolean;
	httpPath?: string;
	useSsl?: boolean;
	useCustomJdbc?: boolean;
	customJdbcUrl?: string;
	lastTestElapsedMillis?: number;
	engineVersion?: string | null;
	driverVersion?: string | null;
	lastVerifiedAt?: string;
	lastUpdatedAt?: string;
	lastHeartbeatAt?: string;
	heartbeatStatus?: string;
	heartbeatFailureCount?: number;
	lastError?: string;
	defaulted?: boolean;
	destinationId?: string;
	destinationDefinitionId?: string;
	destinationName?: string;
	destinationConfig?: Record<string, any>;
}

export interface InfraFeatureFlags {
	multiSourceEnabled?: boolean;
	hasActiveInceptor?: boolean;
	inceptorStatus?: string;
	syncInProgress?: boolean;
	defaultJdbcUrl?: string;
	loginPrincipal?: string;
	lastVerifiedAt?: string;
	lastUpdatedAt?: string;
	dataSourceName?: string;
	description?: string;
	authMethod?: string;
	database?: string;
	proxyUser?: string;
	engineVersion?: string;
	driverVersion?: string;
	lastTestElapsedMillis?: number;
	lastHeartbeatAt?: string;
	heartbeatStatus?: string;
	moduleStatuses?: Array<{ module: string; status: string; description?: string; updatedAt?: string }>;
	integrationStatus?: {
		inProgress?: boolean;
		reason?: string;
		actions?: string[];
		lastSyncAt?: string;
	};
}

export interface ConnectionTestLog {
	id?: string;
	dataSourceId?: string;
	result?: string;
	message?: string;
	elapsedMs?: number;
	createdAt?: string;
}

export interface JdbcDriverInfo {
	fileName: string;
	version?: string;
	label?: string;
}

export interface InfraServiceSettingsPayload {
	service: string;
	settings: Record<string, any>;
}

export interface InfraServiceTestResult {
	success?: boolean;
	message?: string;
	status?: number;
	body?: string;
}

export interface InfraDataSource {
	id?: string;
	name: string;
	type: string;
	jdbcUrl: string;
	username?: string;
	description?: string;
	props?: Record<string, any>;
	status?: string;
	hasSecrets?: boolean;
	defaulted?: boolean;
	createdAt?: string;
	lastUpdatedAt?: string;
	lastVerifiedAt?: string;
	lastTestElapsedMillis?: number;
	lastHeartbeatAt?: string;
	heartbeatStatus?: string;
	heartbeatFailureCount?: number;
	lastError?: string;
}

export interface UpsertInfraDataSourcePayload {
	name: string;
	type: string;
	jdbcUrl: string;
	username?: string;
	description?: string;
	props?: Record<string, any>;
	secrets?: Record<string, any>;
	defaulted?: boolean;
}
