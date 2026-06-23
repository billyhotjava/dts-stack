import type { ProjectSpace } from "@/types/projectSpace";

/**
 * 种子项目空间（部门内 dev 辅助分组）。
 * 不承载黄金主线度量——那些归口部门；这里只是组织 ELT/建模工作的容器。
 */
export const SEED_PROJECT_SPACES: ProjectSpace[] = [
	{ id: "ps-sales-prep", departmentId: "dept-sales", name: "销售准备", owner: "测试用户", modelCount: 3, updatedAt: "2026-06-19" },
	{ id: "ps-sales-forecast", departmentId: "dept-sales", name: "销售预测", owner: "测试用户", modelCount: 1, updatedAt: "2026-06-15" },
	{ id: "ps-quality-monthly", departmentId: "dept-quality", name: "质量月报", owner: "网信中心", modelCount: 5, updatedAt: "2026-06-17" },
];
