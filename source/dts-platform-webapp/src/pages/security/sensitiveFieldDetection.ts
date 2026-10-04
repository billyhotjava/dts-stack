import type { DatasetField } from "@/api/platformApi";

export type SensitiveFieldSuggestion = {
	column: string;
	category: string;
	function: "PARTIAL" | "REDACT";
	reason: string;
};
export type ExistingMaskingRule = { dataset?: { id?: string } | null; column?: string };
const rules = [
	{
		category: "认证凭据",
		name: /(?:^|_)(?:password|passwd|pwd|secret|access_token|api_key)$/,
		comment: /密码|口令|密钥|访问令牌/,
		function: "REDACT" as const,
	},
	{
		category: "身份证件",
		name: /(?:^|_)(?:id_card|idcard|identity_card|identity_no|id_number|passport)(?:_no|_number)?$/,
		comment: /身份证|证件号|护照号/,
		function: "PARTIAL" as const,
	},
	{
		category: "联系电话",
		name: /(?:^|_)(?:phone|telephone|mobile|tel)(?:_no|_number)?$/,
		comment: /手机号|电话号码|联系电话/,
		function: "PARTIAL" as const,
	},
	{
		category: "电子邮箱",
		name: /(?:^|_)(?:email|e_mail)(?:_address)?$/,
		comment: /电子邮箱|电子邮件|邮箱地址/,
		function: "PARTIAL" as const,
	},
	{
		category: "银行账号",
		name: /(?:^|_)(?:bank_card|bank_account|credit_card)(?:_no|_number)?$/,
		comment: /银行卡号|银行账号|信用卡号/,
		function: "PARTIAL" as const,
	},
	{
		category: "个人姓名",
		name: /(?:^|_)(?:real_name|full_name|person_name|customer_name|employee_name)$/,
		comment: /真实姓名|个人姓名|客户姓名|员工姓名/,
		function: "PARTIAL" as const,
	},
];
const normalize = (name: string) =>
	name
		.trim()
		.replace(/([a-z0-9])([A-Z])/g, "$1_$2")
		.toLowerCase();

export function detectSensitiveFields(
	fields: DatasetField[],
	existing: ExistingMaskingRule[],
	datasetId: string,
): SensitiveFieldSuggestion[] {
	const covered = new Set(
		existing
			.filter((rule) => !rule.dataset?.id || rule.dataset.id === datasetId)
			.map((rule) => normalize(rule.column || "")),
	);
	const suggestions: SensitiveFieldSuggestion[] = [];
	for (const field of fields) {
		const name = normalize(field.name || "");
		if (!name || covered.has(name)) continue;
		const match = rules.find((rule) => rule.name.test(name) || rule.comment.test(field.comment || ""));
		if (!match) continue;
		covered.add(name);
		suggestions.push({
			column: field.name,
			category: match.category,
			function: match.function,
			reason: match.name.test(name) ? "字段名称匹配" : "字段注释匹配",
		});
	}
	return suggestions;
}
