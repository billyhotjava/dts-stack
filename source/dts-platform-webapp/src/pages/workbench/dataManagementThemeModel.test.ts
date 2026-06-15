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
	it("keeps business themes as the first-class board objects even when no chain exists", () => {
		const themes = buildDataManagementThemes([], {});

		expect(themes.map((item) => item.title)).toEqual(DEFAULT_DATA_MANAGEMENT_THEMES.map((item) => item.title));
		expect(themes.map((item) => item.title)).toEqual(["经营分析", "质量管理", "项目交付", "客户服务"]);
		for (const theme of themes) {
			expect(theme.dataAvailability.label).toBe("未接入");
			expect(theme.governance.label).toBe("待建设");
			expect(theme.consumption.label).toBe("待发布");
			expect(theme.operation.label).toBe("待运行");
			expect(theme.primaryAction.route).toBe("/foundation/data-sources");
		}
	});

	it("assigns golden chains to business themes using customer-facing business words", () => {
		const chains = [
			summary({ chainKey: "chain-orders", displayName: "经营订单分析链路" }),
			summary({ chainKey: "chain-quality", displayName: "质量问题整改链路" }),
			summary({ chainKey: "chain-project", displayName: "项目交付进度链路" }),
			summary({ chainKey: "chain-customer", displayName: "客户服务满意度链路" }),
		];

		const themes = stateByTheme(buildDataManagementThemes(chains, {}));

		expect(themes.business.chainCount).toBe(1);
		expect(themes.quality.chainCount).toBe(1);
		expect(themes.delivery.chainCount).toBe(1);
		expect(themes.customer.chainCount).toBe(1);
		expect(themes.business.relatedChains[0].displayName).toBe("经营订单分析链路");
		expect(themes.quality.relatedChains[0].displayName).toBe("质量问题整改链路");
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

		expect(themes.quality.governance.label).toBe("待治理");
		expect(themes.quality.governance.tone).toBe("warning");
		expect(themes.quality.operation.label).toBe("待运行");
		expect(themes.quality.primaryAction.label).toBe("补齐质量责任人与分级说明");
		expect(themes.quality.primaryAction.route).toBe("/governance/quality");
		expect(themes.quality.evidenceRefs).toContain("it/evidence/customer-demo/03-governance-gate.md");
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

		expect(themes.customer.dataAvailability.label).toBe("可用");
		expect(themes.customer.governance.label).toBe("已达标");
		expect(themes.customer.consumption.label).toBe("已发布");
		expect(themes.customer.operation.label).toBe("运行健康");
		expect(themes.customer.primaryAction.route).toBe("/bi/report-factory");
	});
});
