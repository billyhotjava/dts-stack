const ERROR_CODE_MESSAGES: Record<string, string> = {
	SQL_TEMPLATE_PARAM_MISSING: "SQL 参数缺失，请补全筛选参数后重试",
	SQL_TEMPLATE_PARAM_UNSUPPORTED: "参数名不在 SQL 模板白名单中",
	SQL_TEMPLATE_PARAM_INVALID: "参数名格式不合法，请仅使用字母、数字、下划线",
	SQL_READ_ONLY_REQUIRED: "仅允许只读 SQL（SELECT/WITH）",
	SQL_MULTI_STATEMENT_BLOCKED: "禁止执行多语句 SQL",
	SQL_DANGEROUS_STATEMENT_BLOCKED: "检测到危险 SQL，已阻止执行",
	SQL_TOO_LONG: "SQL 语句过长，请拆分后重试",
	SQL_EMPTY: "SQL 为空或无效",
	EXT_DB_CONNECTION_REFUSED: "外部数据库连接被拒绝，请检查网络与端口",
	EXT_DB_TIMEOUT: "外部数据库连接超时，请检查网络或负载",
	EXT_DB_CONNECT_FAILED: "外部数据库连接失败",
	DB_QUERY_FAILED: "数据库查询失败",
};

export function resolveAnalyticsErrorCodeMessage(code?: string | null): string | undefined {
	if (!code) {
		return undefined;
	}
	const normalized = String(code).trim().toUpperCase();
	if (!normalized) {
		return undefined;
	}
	return ERROR_CODE_MESSAGES[normalized];
}
