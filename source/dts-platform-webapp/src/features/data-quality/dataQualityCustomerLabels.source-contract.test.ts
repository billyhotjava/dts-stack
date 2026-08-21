import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const source = (relativePath: string) => readFileSync(resolve(process.cwd(), relativePath), "utf8");

describe("data quality customer-facing labels", () => {
	it("keeps run filter values while showing Chinese status and trigger labels", () => {
		const shared = source("src/features/data-quality/QualityShared.tsx");
		const runs = source("src/features/data-quality/RunPages.tsx");

		expect(shared).toContain("statusLabel(normalized)");
		expect(runs).toContain('["QUEUED", "RUNNING", "PASSED", "SUCCESS", "SUCCEEDED", "FAILED", "ERROR", "SKIPPED"]');
		expect(runs).toContain("label: displayName(value)");
		expect(runs).toContain("qualityRuleNameLabel(item.name || item.id)");
		expect(runs).toContain('qualityLabel(run.errorCategory, "请检查失败样本与执行 SQL。")');
		expect(runs).not.toMatch(/toLocaleString\(\)\}\sms/);
	});

	it("removes English chrome and localizes issue action enums", () => {
		const workspace = source("src/features/data-quality/QualityWorkspace.tsx");
		const disposition = source("src/features/data-quality/RunIssueDisposition.tsx");

		expect(workspace).toContain("数据质量中心");
		expect(workspace).not.toContain("DATA QUALITY CENTER");
		expect(disposition).toContain('qualityLabel(value, "其他操作")');
		expect(disposition).not.toContain('<Tag>{String(value || "-")}</Tag>');
	});

	it("localizes rule dimensions and severity without changing form values", () => {
		const editor = source("src/features/data-quality/RuleEditorPage.tsx");
		const templates = source("src/features/data-quality/TemplatePages.tsx");
		const configuration = source("src/features/data-quality/ConfigurationPages.tsx");

		expect(editor).toContain('const TYPE_OPTIONS = ["COMPLETENESS"');
		expect(editor).toContain("label: displayName(value)");
		expect(templates).toContain('value: "WARN", label: "告警"');
		expect(templates).toContain("render: (value) => displayName(value)");
		expect(configuration).toContain('title: "数据库模式"');
		expect(configuration).toContain("label={displayName(item.type)}");
	});

	it("removes internal sprint wording from quality rule names", () => {
		const types = source("src/features/data-quality/qualityTypes.ts");
		expect(types).toContain("qualityRuleNameLabel");
		expect(types).toContain("/^Sprint\\s*\\d+");
		expect(types).toContain('.replace(/KPI/gi, "指标")');
	});
});
