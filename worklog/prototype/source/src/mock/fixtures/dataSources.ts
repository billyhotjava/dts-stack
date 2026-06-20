import type { DataSource } from "@/types/datasource";

/**
 * 种子数据源 —— 体现混合制归属：
 * - PLM/ERP/QMIS 为全所级系统，scope=platform（平台层共享，跨部门）
 * - 各部门本地源 scope=workspace（归属对应工作区）
 */
export const SEED_DATA_SOURCES: DataSource[] = [
	{ id: "ds-plm", name: "PLM 生产系统", type: "PLM", scope: "platform", connector: "Oracle", status: "connected", owner: "网信中心" },
	{ id: "ds-erp", name: "ERP 企业系统", type: "ERP", scope: "platform", connector: "SQLServer", status: "connected", owner: "网信中心" },
	{ id: "ds-qmis", name: "QMIS 质量系统", type: "QMIS", scope: "platform", connector: "MySQL", status: "connected", owner: "网信中心" },
	{
		id: "ds-sales-local",
		name: "销售本地台账",
		type: "Excel",
		scope: "workspace",
		workspaceId: "ws-sales",
		connector: "File",
		status: "untested",
		owner: "销售处",
	},
	{
		id: "ds-quality-local",
		name: "质量抽检台账",
		type: "PostgreSQL",
		scope: "workspace",
		workspaceId: "ws-quality",
		connector: "PostgreSQL",
		status: "connected",
		owner: "质量处",
	},
];
