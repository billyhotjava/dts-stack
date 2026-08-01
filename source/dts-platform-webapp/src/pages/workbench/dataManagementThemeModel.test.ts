import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import type {
	GoldenChainDetail,
	GoldenChainStage,
	GoldenChainStageSnapshot,
	GoldenChainSummary,
} from "@/api/services/goldenChainService";
import {
	buildDataManagementThemes,
	type DataManagementThemeState,
	DEFAULT_DATA_MANAGEMENT_THEMES,
} from "./dataManagementThemeModel";

const summary = (overrides: Partial<GoldenChainSummary>): GoldenChainSummary => ({
	chainKey: "chain-orders",
	displayName: "现场订单履约链路",
	sourceKind: "JDBC",
	currentStage: "CONSUMABLE",
	currentStageLabel: "可消费",
	status: "READY",
	owner: "数据管理员",
	...overrides,
});

const stage = (
	stageName: GoldenChainStage,
	status: GoldenChainStageSnapshot["status"],
	overrides: Partial<GoldenChainStageSnapshot> = {},
): GoldenChainStageSnapshot => ({
	stage: stageName,
	stageLabel: stageName,
	status,
	owner: "数据管理员",
	...overrides,
});

const detail = (chain: GoldenChainSummary, stages: GoldenChainStageSnapshot[]): GoldenChainDetail => ({
	...chain,
	stages,
});

const stateByTheme = (themes: DataManagementThemeState[]) => Object.fromEntries(themes.map((item) => [item.key, item]));

describe("data management theme model", () => {
	it("keeps test fixtures free from builtin customer demo scene wording", () => {
		const source = readFileSync(new URL("./dataManagementThemeModel.test.ts", import.meta.url), "utf8");

		expect(source).not.toContain("customer" + "-demo");
		expect(source).not.toContain("客户" + "服务");
	});

	it("does not create default customer business themes before onsite definition", () => {
		const themes = buildDataManagementThemes([], {});

		expect(DEFAULT_DATA_MANAGEMENT_THEMES).toEqual([]);
		expect(themes).toEqual([]);
	});

	it("builds board objects only from configured customer chains", () => {
		const chains = [
			summary({ chainKey: "chain-orders", displayName: "现场订单履约链路" }),
			summary({ chainKey: "chain-quality", displayName: "现场质量整改链路" }),
		];

		const themes = stateByTheme(buildDataManagementThemes(chains, {}));

		expect(Object.keys(themes)).toEqual(["chain-orders", "chain-quality"]);
		expect(themes["chain-orders"].chainCount).toBe(1);
		expect(themes["chain-quality"].chainCount).toBe(1);
		expect(themes["chain-orders"].title).toBe("现场订单履约链路");
		expect(themes["chain-quality"].title).toBe("现场质量整改链路");
	});

	it("surfaces blocked governance and operations as theme-level next actions", () => {
		const chain = summary({
			chainKey: "chain-quality",
			displayName: "现场质量整改链路",
			currentStage: "GOVERNANCE_READY",
			currentStageLabel: "治理校验",
			status: "BLOCKED",
		});
		const themes = stateByTheme(
			buildDataManagementThemes([chain], {
				"chain-quality": detail(chain, [
					stage("ODS_READY", "READY"),
					stage("GOVERNANCE_READY", "BLOCKED", {
						failureReason: "缺少质量责任人",
						nextAction: "补齐质量责任人与分级说明",
						evidenceRef: "it/evidence/onsite-defined/03-governance-gate.md",
					}),
					stage("CONSUMABLE", "PENDING"),
					stage("OPERATED", "PENDING"),
				]),
			}),
		);

		expect(themes["chain-quality"].governance.label).toBe("待治理");
		expect(themes["chain-quality"].governance.tone).toBe("warning");
		expect(themes["chain-quality"].operation.label).toBe("待运行");
		expect(themes["chain-quality"].primaryAction.label).toBe("补齐质量责任人与分级说明");
		expect(themes["chain-quality"].primaryAction.route).toBe("/governance/rules/runs");
		expect(themes["chain-quality"].evidenceRefs).toContain("it/evidence/onsite-defined/03-governance-gate.md");
	});

	it("routes blocked model readiness to the prototype-owned model workbench", () => {
		const chain = summary({
			chainKey: "chain-model",
			displayName: "现场模型链路",
			currentStage: "MODEL_READY",
			currentStageLabel: "模型就绪",
			status: "BLOCKED",
		});
		const themes = stateByTheme(
			buildDataManagementThemes([chain], {
				"chain-model": detail(chain, [
					stage("ODS_READY", "READY"),
					stage("MODEL_READY", "BLOCKED", {
						failureReason: "缺少语义模型绑定",
						nextAction: "补齐模型字段与业务对象映射",
					}),
				]),
			}),
		);

		expect(themes["chain-model"].primaryAction.route).toBe("/data-modeling/dimensions/workbench");
	});

	it("marks a theme as published and healthy only when consumption and operations are ready", () => {
		const chain = summary({
			chainKey: "chain-onsite-satisfaction",
			displayName: "现场满意度链路",
			currentStage: "OPERATED",
			currentStageLabel: "运行中",
			status: "READY",
		});
		const themes = stateByTheme(
			buildDataManagementThemes([chain], {
				"chain-customer": detail(chain, [
					stage("ODS_READY", "READY"),
					stage("GOVERNANCE_READY", "READY"),
					stage("CONSUMABLE", "READY"),
					stage("OPERATED", "READY", { evidenceRef: "ops/onsite-satisfaction-ready.md" }),
				]),
			}),
		);

		expect(themes["chain-onsite-satisfaction"].dataAvailability.label).toBe("可用");
		expect(themes["chain-onsite-satisfaction"].governance.label).toBe("已达标");
		expect(themes["chain-onsite-satisfaction"].consumption.label).toBe("已发布");
		expect(themes["chain-onsite-satisfaction"].operation.label).toBe("运行健康");
		expect(themes["chain-onsite-satisfaction"].primaryAction.route).toBe("/bi/report-factory");
	});
});
