export type MetricStatus = "published" | "draft";

/** 指标 —— 归口部门，基于已发布数据集设计。 */
export interface Metric {
	id: string;
	departmentId: string;
	name: string;
	/** 稳定 ASCII 编码（避免中文名漂移） */
	code: string;
	/** 口径说明 */
	caliber: string;
	/** 计算表达式 */
	expression: string;
	unit: string;
	/** 关联数据集 */
	datasetId: string;
	status: MetricStatus;
	owner?: string;
	updatedAt?: string;
	/** 看板：当前值 / 目标 / 走势 */
	currentValue?: number;
	target?: number;
	trend?: number[];
}

/** 语义主题域 —— 组织度量与维度。 */
export interface SemanticSubject {
	id: string;
	departmentId: string;
	name: string;
	/** 关联数据集对象 */
	datasetId: string;
	metricCodes: string[];
	dimensions: string[];
	status: MetricStatus;
}

/** 业务术语。 */
export interface GlossaryTerm {
	id: string;
	departmentId: string;
	term: string;
	definition: string;
}

/** 参考码（码表）。 */
export interface ReferenceCode {
	id: string;
	codeType: string;
	code: string;
	name: string;
}
