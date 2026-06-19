import type { Project } from "@/types/project";
import { SEED_PROJECTS } from "./fixtures/projects";

/**
 * 进程内 mock 数据库。各 service 从这里读写。
 * 后续 sprint 的领域数据（数据源/转换/资产/指标）按同样方式扩展集合。
 */
interface MockDb {
	projects: Project[];
}

/** Chrome 95 安全的深拷贝（数据皆为纯 JSON，避免 structuredClone：Chrome 98+）。 */
function clone<T>(value: T): T {
	return JSON.parse(JSON.stringify(value)) as T;
}

function seed(): MockDb {
	// 深拷贝，避免页面变更污染种子常量
	return {
		projects: clone(SEED_PROJECTS),
	};
}

export const db: MockDb = seed();

/** 重置为初始样例数据（开发入口调用）。 */
export function resetDb(): void {
	const fresh = seed();
	db.projects = fresh.projects;
}
