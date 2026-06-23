import type { Result } from "@/types/api";
import type { TransformGraphDTO } from "@/types/transform";
import { ok } from "../client";
import { db } from "../db";

/** 转换图服务 —— 按项目空间取/存 ELT 画布。 */
export const transformService = {
	getGraph(projectSpaceId: string): Promise<Result<TransformGraphDTO>> {
		const found = db.transformGraphs.find((g) => g.projectSpaceId === projectSpaceId);
		return ok(found ?? { projectSpaceId, nodes: [], edges: [] });
	},
};
