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
