import type { Connector } from "@/types/datasource";

/** 平台层登记的连接器类型（跨部门共享）。 */
export const SEED_CONNECTORS: Connector[] = [
	{ key: "Oracle", name: "Oracle", category: "关系型数据库", engines: ["Oracle 11g+", "Oracle 19c"], status: "enabled" },
	{ key: "SQLServer", name: "SQL Server", category: "关系型数据库", engines: ["SQL Server 2016+"], status: "enabled" },
	{ key: "MySQL", name: "MySQL", category: "关系型数据库", engines: ["MySQL 5.7", "MySQL 8.0"], status: "enabled" },
	{ key: "PostgreSQL", name: "PostgreSQL", category: "关系型数据库", engines: ["PostgreSQL 12+"], status: "enabled" },
	{ key: "Hive", name: "Hive", category: "大数据", engines: ["Hive 2.x", "Hive 3.x"], status: "enabled" },
	{ key: "File", name: "文件 / Excel", category: "文件", engines: ["CSV", "XLSX"], status: "enabled" },
	{ key: "Kafka", name: "Kafka", category: "消息流", engines: ["Kafka 2.x+"], status: "disabled" },
];
