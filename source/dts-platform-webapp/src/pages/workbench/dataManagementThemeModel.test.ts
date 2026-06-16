import { describe, expect, it } from "vitest";
import type {
	GoldenChainDetail,
	GoldenChainStage,
	GoldenChainStageSnapshot,
	GoldenChainSummary,
} from "@/api/services/goldenChainService";
import {
	buildDataManagementThemes,
	DEFAULT_DATA_MANAGEMENT_THEMES,
	type DataManagementThemeState,
} from "./dataManagementThemeModel";

const summary = (overrides: Partial<GoldenChainSummary>): GoldenChainSummary => ({
	chainKey: "chain-orders",
	displayName: "经营订单分析链路",
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

const detail = (
	chain: GoldenChainSummary,
	stages: GoldenChainStageSnapshot[],
): GoldenChainDetail => ({
	...chain,
	stages,
});

const stateByTheme = (themes: DataManagementThemeState[]) =>
	Object.fromEntries(themes.map((item) => [item.key, item]));

describe("data management theme model", () => {
	it("does not create default customer business themes before onsite definition", () => {
		const themes = buildDataManagementThemes([], {});

		expect(DEFAULT_DATA_MANAGEMENT_THEMES).toEqual([]);
		expect(themes).toEqual([]);
	});

	it("builds board objects only from configured customer chains", () => {
		const chains = [
			summary({ chainKey: "chain-orders", displayName: "经营订单分析链路" }),
			summary({ chainKey: "chain-quality", displayName: "质量问题整改链路" }),
		];

		const themes = stateByTheme(buildDataManagementThemes(chains, {}));

		expect(Object.keys(themes)).toEqual(["chain-orders", "chain-quality"]);
		expect(themes["chain-orders"].chainCount).toBe(1);
		expect(themes["chain-quality"].chainCount).toBe(1);
		expect(themes["chain-orders"].title).toBe("经营订单分析链路");
		expect(themes["chain-quality"].title).toBe("质量问题整改链路");
	});

	it("surfaces blocked governance and operations as theme-level next actions", () => {
		const chain = summary({
			chainKey: "chain-quality",
			displayName: "质量问题整改链路",
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
						evidenceRef: "it/evidence/customer-demo/03-governance-gate.md",
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
		expect(themes["chain-quality"].primaryAction.route).toBe("/governance/quality");
		expect(themes["chain-quality"].evidenceRefs).toContain("it/evidence/customer-demo/03-governance-gate.md");
	});

	it("marks a theme as published and healthy only when consumption and operations are ready", () => {
		const chain = summary({
			chainKey: "chain-customer",
			displayName: "客户服务满意度链路",
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
					stage("OPERATED", "READY", { evidenceRef: "ops/customer-service-ready.md" }),
				]),
			}),
		);

		expect(themes["chain-customer"].dataAvailability.label).toBe("可用");
		expect(themes["chain-customer"].governance.label).toBe("已达标");
		expect(themes["chain-customer"].consumption.label).toBe("已发布");
		expect(themes["chain-customer"].operation.label).toBe("运行健康");
		expect(themes["chain-customer"].primaryAction.route).toBe("/bi/report-factory");
	});
});
