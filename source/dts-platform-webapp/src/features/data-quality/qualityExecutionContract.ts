import api from "@/api/apiClient";

export type QualityDiagnostic = { statementKey: string; reasonCode: string; detail?: string | null };
export type QualityOutcome = {
 schemaVersion: number;
 qualityOutcome: "PASSED" | "VIOLATION" | "UNKNOWN";
 executionOutcome: "OK" | "FAILED";
 statisticsStatus: "EXACT" | "UNDEDUPLICATED" | "UNAVAILABLE";
 violationOccurrences?: number | null;
 diagnostics: QualityDiagnostic[];
};
export type QualityValidation = { valid: boolean; checksum: string; diagnostics: QualityDiagnostic[]; allowedFunctions: string };
export type QualityPreview = { checksum: string; outcome: QualityOutcome; rowsTotal?: number; failingRowCount?: number };
export const validateQualityDraft = (datasetId: string, sql: string) =>
 api.post<QualityValidation>({ url: "/governance/quality/rules/validate-sql", data: { datasetId, definition: { sql } } });
export const previewQualityDraft = (datasetId: string, sql: string) =>
 api.post<QualityPreview>({ url: "/governance/quality/rules/dry-run", data: { datasetId, definition: { sql } } });

const REASONS: Record<string, string> = {
 UNSUPPORTED_FUNCTION: "函数不受支持，请使用允许的函数",
 UNSUPPORTED_CAST_TYPE: "类型转换不受支持，请使用允许的类型",
 UNSUPPORTED_SYNTAX: "语法不受支持，请改写为只读 SELECT",
 OUT_OF_SCOPE_TABLE: "只能引用当前绑定资产，请核对 schema.table",
 UNSUPPORTED_SOURCE_TYPE: "当前数据源类型不支持此检测方式",
 INVALID_BOUND_TABLE: "资产物理库表信息不完整，请完善资产信息",
 UNPARSEABLE_SQL: "SQL 无法解析，请检查语法并只提交一条查询",
 NO_TABLE_REFERENCE: "查询必须引用当前绑定资产",
 NO_STATEMENTS: "请输入检测 SQL",
 WRITE_BLOCKED: "仅允许只读查询，请移除写入或结构修改语句",
 DATASET_SCOPE_BLOCKED: "查询不符合绑定资产约束，请重新校验 SQL",
 RESULT_ID_REQUIRED: "历史运行受 id 统计限制，请使用修复后的规则重新运行",
 QUALITY_VIOLATION: "已发现不符合规则的数据，请查看失败样本",
 SAMPLE_FAILED: "违规结论已保留，样本采集失败；请检查连接后重新运行",
 STATISTICS_UNDEDUPLICATED: "多语句违规数量未去重，不计算精确通过率",
 STATISTICS_OVERFLOW: "统计数量超出支持范围，请缩小检查范围",
 CONNECTION_ERROR: "数据源连接失败，请检查连接后重试",
 TIMEOUT: "执行超时，请检查查询范围和数据源负载",
 SQL_SYNTAX: "SQL 语法不正确，请重新校验",
 OBJECT_NOT_FOUND: "绑定资产的表或字段不可用，请核对资产元数据",
 PERMISSION_DENIED: "数据源读取权限不足，请联系管理员",
 EXECUTION_ERROR: "检测执行未完成，请使用运行编号排查后重试",
 UNKNOWN: "执行结果不确定，请使用运行编号排查",
 DISPATCH_REJECTED: "执行任务未能提交，请稍后重试",
 RESULT_CONTRACT_INVALID: "运行结果不可读取，请联系管理员核对版本",
};
export const qualityDiagnosticText = (diagnostic: QualityDiagnostic) =>
 `${REASONS[diagnostic.reasonCode] || diagnostic.reasonCode}${diagnostic.detail ? `（${diagnostic.detail}）` : ""}`;
