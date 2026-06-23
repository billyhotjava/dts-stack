/**
 * 数据源归属：混合制。
 * - platform：全所级系统（PLM/ERP/QMIS），网信中心平台层统一登记 + 密钥集中管控，跨部门共享
 * - department：部门本地源（用户本地），由对应部门登记自管
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
}
