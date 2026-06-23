import type { AssetGrant, Dataset, DataProduct, DatasetLineage, QualityRule } from "@/types/asset";
import type { Connector, DataSource, JdbcDriver } from "@/types/datasource";
import type { Department } from "@/types/department";
import type { ProjectSpace } from "@/types/projectSpace";
import type { TransformGraphDTO } from "@/types/transform";
import {
	SEED_ASSET_GRANTS,
	SEED_DATA_PRODUCTS,
	SEED_DATASETS,
	SEED_LINEAGE,
	SEED_QUALITY_RULES,
} from "./fixtures/assets";
import { SEED_CONNECTORS } from "./fixtures/connectors";
import { SEED_DATA_SOURCES } from "./fixtures/dataSources";
import { SEED_DEPARTMENTS } from "./fixtures/departments";
import { SEED_JDBC_DRIVERS } from "./fixtures/jdbcDrivers";
import { SEED_PROJECT_SPACES } from "./fixtures/projectSpaces";
import { SEED_TRANSFORM_GRAPHS } from "./fixtures/transformGraphs";

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
	transformGraphs: TransformGraphDTO[];
	datasets: Dataset[];
	dataProducts: DataProduct[];
	lineage: Record<string, DatasetLineage>;
	qualityRules: QualityRule[];
	assetGrants: AssetGrant[];
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
		transformGraphs: clone(SEED_TRANSFORM_GRAPHS),
		datasets: clone(SEED_DATASETS),
		dataProducts: clone(SEED_DATA_PRODUCTS),
		lineage: clone(SEED_LINEAGE),
		qualityRules: clone(SEED_QUALITY_RULES),
		assetGrants: clone(SEED_ASSET_GRANTS),
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
	db.transformGraphs = fresh.transformGraphs;
	db.datasets = fresh.datasets;
	db.dataProducts = fresh.dataProducts;
	db.lineage = fresh.lineage;
	db.qualityRules = fresh.qualityRules;
	db.assetGrants = fresh.assetGrants;
}
