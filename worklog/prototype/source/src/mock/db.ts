import type { DataSource } from "@/types/datasource";
import type { Project } from "@/types/project";
import type { Workspace } from "@/types/workspace";
import { SEED_DATA_SOURCES } from "./fixtures/dataSources";
import { SEED_PROJECTS } from "./fixtures/projects";
import { SEED_WORKSPACES } from "./fixtures/workspaces";

/**
 * 进程内 mock 数据库。各 service 从这里读写。
 * 后续 sprint 的领域数据（转换/资产/指标）按同样方式扩展集合。
 */
interface MockDb {
	workspaces: Workspace[];
	projects: Project[];
	dataSources: DataSource[];
}

/** Chrome 95 安全的深拷贝（数据皆纯 JSON，避免 structuredClone：Chrome 98+）。 */
function clone<T>(value: T): T {
	return JSON.parse(JSON.stringify(value)) as T;
}

function seed(): MockDb {
	return {
		workspaces: clone(SEED_WORKSPACES),
		projects: clone(SEED_PROJECTS),
		dataSources: clone(SEED_DATA_SOURCES),
	};
}

export const db: MockDb = seed();

/** 重置为初始样例数据（开发入口调用）。 */
export function resetDb(): void {
	const fresh = seed();
	db.workspaces = fresh.workspaces;
	db.projects = fresh.projects;
	db.dataSources = fresh.dataSources;
}
