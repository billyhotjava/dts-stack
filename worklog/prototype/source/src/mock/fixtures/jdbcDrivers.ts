import type { JdbcDriver } from "@/types/datasource";

/** 平台层管理的 JDBC 驱动。 */
export const SEED_JDBC_DRIVERS: JdbcDriver[] = [
	{ id: "drv-ojdbc8", name: "Oracle ojdbc8", version: "21.9.0", connectorKey: "Oracle", fileName: "ojdbc8-21.9.0.jar", uploadedAt: "2026-04-01", status: "active" },
	{ id: "drv-mssql", name: "MSSQL JDBC", version: "12.4.2", connectorKey: "SQLServer", fileName: "mssql-jdbc-12.4.2.jar", uploadedAt: "2026-04-01", status: "active" },
	{ id: "drv-mysql", name: "MySQL Connector/J", version: "8.3.0", connectorKey: "MySQL", fileName: "mysql-connector-j-8.3.0.jar", uploadedAt: "2026-04-05", status: "active" },
	{ id: "drv-pg", name: "PostgreSQL JDBC", version: "42.7.3", connectorKey: "PostgreSQL", fileName: "postgresql-42.7.3.jar", uploadedAt: "2026-05-02", status: "active" },
	{ id: "drv-hive", name: "Hive JDBC", version: "3.1.3", connectorKey: "Hive", fileName: "hive-jdbc-3.1.3.jar", uploadedAt: "2026-05-20", status: "inactive" },
];
