import type { Connector, DataSource, JdbcDriver } from "@/types/datasource";
import type { Department } from "@/types/department";
import type { ProjectSpace } from "@/types/projectSpace";
import { SEED_CONNECTORS } from "./fixtures/connectors";
import { SEED_DATA_SOURCES } from "./fixtures/dataSources";
import { SEED_DEPARTMENTS } from "./fixtures/departments";
import { SEED_JDBC_DRIVERS } from "./fixtures/jdbcDrivers";
import { SEED_PROJECT_SPACES } from "./fixtures/projectSpaces";

/**
 * 进程内 mock 数据库。各 service 从这里读写。
 * 组织模型：部门(主) → 项目空间(dev 辅助)；资产/指标归口部门。
 */
interface MockDb {
	departments: Department[];
	projectSpaces: ProjectSpace[];
	dataSources: DataSource[];
	connectors: Connector[];
	jdbcDrivers: JdbcDriver[];
}

/** Chrome 95 安全的深拷贝（数据皆纯 JSON，避免 structuredClone：Chrome 98+）。 */
function clone<T>(value: T): T {
	return JSON.parse(JSON.stringify(value)) as T;
}

function seed(): MockDb {
	return {
		departments: clone(SEED_DEPARTMENTS),
		projectSpaces: clone(SEED_PROJECT_SPACES),
		dataSources: clone(SEED_DATA_SOURCES),
		connectors: clone(SEED_CONNECTORS),
		jdbcDrivers: clone(SEED_JDBC_DRIVERS),
	};
}

export const db: MockDb = seed();

/** 重置为初始样例数据（开发入口调用）。 */
export function resetDb(): void {
	const fresh = seed();
	db.departments = fresh.departments;
	db.projectSpaces = fresh.projectSpaces;
	db.dataSources = fresh.dataSources;
	db.connectors = fresh.connectors;
	db.jdbcDrivers = fresh.jdbcDrivers;
}
