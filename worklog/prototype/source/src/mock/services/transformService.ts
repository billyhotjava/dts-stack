import type { Result } from "@/types/api";
import type { TransformGraphDTO } from "@/types/transform";
import { ok } from "../client";
import { db } from "../db";

const LS_PREFIX = "dts-proto-graph-";
const lsKey = (id: string) => `${LS_PREFIX}${id}`;

/** 转换图服务 —— 按项目空间取/存 ELT 画布；改动落 localStorage（跨刷新存活）+ 回写内存 db。 */
export const transformService = {
	getGraph(projectSpaceId: string): Promise<Result<TransformGraphDTO>> {
		// 优先读已持久化的用户改动
		try {
			const raw = localStorage.getItem(lsKey(projectSpaceId));
			if (raw) return ok(JSON.parse(raw) as TransformGraphDTO);
		} catch {
			/* localStorage 不可用时回落到种子 */
		}
		const found = db.transformGraphs.find((g) => g.projectSpaceId === projectSpaceId);
		return ok(found ?? { projectSpaceId, nodes: [], edges: [] });
	},

	saveGraph(dto: TransformGraphDTO): Promise<Result<boolean>> {
		try {
			localStorage.setItem(lsKey(dto.projectSpaceId), JSON.stringify(dto));
		} catch {
			/* 忽略持久化失败 */
		}
		const exists = db.transformGraphs.some((g) => g.projectSpaceId === dto.projectSpaceId);
		db.transformGraphs = exists
			? db.transformGraphs.map((g) => (g.projectSpaceId === dto.projectSpaceId ? dto : g))
			: [...db.transformGraphs, dto];
		return ok(true);
	},

	/** 清除所有已持久化的画布改动（重置样例数据时调用）。 */
	clearPersisted(): void {
		try {
			Object.keys(localStorage)
				.filter((k) => k.startsWith(LS_PREFIX))
				.forEach((k) => localStorage.removeItem(k));
		} catch {
			/* 忽略 */
		}
	},
};
