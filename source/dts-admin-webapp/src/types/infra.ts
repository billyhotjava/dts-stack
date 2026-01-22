export type HiveAuthMethod = "KEYTAB" | "PASSWORD";

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
}

export interface HiveConnectionPersistRequest extends HiveConnectionTestRequest {
	name: string;
	description?: string;
	servicePrincipal: string;
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
}

export interface HiveConnectionTestResult {
	success: boolean;
	message: string;
	elapsedMillis: number;
	engineVersion?: string | null;
	driverVersion?: string | null;
	warnings: string[];
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
