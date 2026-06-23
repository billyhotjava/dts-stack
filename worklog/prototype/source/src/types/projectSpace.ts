/**
 * 项目空间 = 部门内的辅助分组（dev 层），用于组织 ELT/建模 工作。
 * 对齐现网"项目空间"(dbt 作用域)。它不拥有资产/指标——那些归口部门。
 */
export interface ProjectSpace {
	id: string;
	/** 所属部门 */
	departmentId: string;
	name: string;
	owner?: string;
	/** dev 统计：该空间下的模型/转换数（辅助信息，非黄金主线判据） */
	modelCount?: number;
	updatedAt?: string;
}
