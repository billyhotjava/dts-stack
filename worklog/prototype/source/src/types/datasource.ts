/**
 * 数据源归属：混合制。
 * - platform：全所级系统（PLM/ERP/QMIS），网信中心平台层统一登记 + 密钥集中管控，跨部门共享（部门侧只读）
 * - department：部门本地源，由对应部门登记自管（部门侧可增删改）
 */
export type DataSourceScope = "platform" | "department";

export type DataSourceStatus = "connected" | "error" | "untested";

export interface DataSource {
	id: string;
	name: string;
	/** 系统类型：PLM/ERP/QMIS/Excel/PostgreSQL… */
	type: string;
	scope: DataSourceScope;
	/** scope=department 时归属的部门 */
	departmentId?: string;
	connector: string;
	status: DataSourceStatus;
	owner?: string;
	// —— 详情字段 ——
	jdbcUrl?: string;
	username?: string;
	description?: string;
	capabilities?: string[];
	engineVersion?: string;
	driverVersion?: string;
	lastTestedAt?: string;
	createdAt?: string;
}

/** 新建/编辑数据源的提交体（对齐现网 DataSourceUpsertPayload 形状）。 */
export interface DataSourceUpsertPayload {
	name: string;
	type: string;
	connector: string;
	jdbcUrl?: string;
	username?: string;
	description?: string;
}

/** 连通测试结果（对齐现网 ConnectionTestResult）。 */
export interface ConnectionTestResult {
	success: boolean;
	message?: string;
	elapsedMillis?: number;
	engineVersion?: string;
	driverVersion?: string;
	warnings?: string[];
}

/** 连接器（平台层登记的可用连接器类型）。 */
export interface Connector {
	key: string;
	name: string;
	category: string;
	/** 支持的引擎/数据库 */
	engines: string[];
	status: "enabled" | "disabled";
}

/** JDBC 驱动（平台层管理）。 */
export interface JdbcDriver {
	id: string;
	name: string;
	version: string;
	connectorKey: string;
	fileName: string;
	uploadedAt: string;
	status: "active" | "inactive";
}
