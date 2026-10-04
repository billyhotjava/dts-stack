// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeAll, beforeEach, describe, expect, it } from "vitest";
import type { ModelSpecStageGate } from "@/api/modelSpecApi";
import { ModelQualityConstraintPanel } from "./ModelQualityConstraintPanel";

beforeAll(() => {
	if (!window.matchMedia) {
		Object.defineProperty(window, "matchMedia", {
			writable: true,
			value: (query: string) => ({
				matches: false,
				media: query,
				onchange: null,
				addListener: () => {},
				removeListener: () => {},
				addEventListener: () => {},
				removeEventListener: () => {},
				dispatchEvent: () => false,
			}),
		});
	}
});

const gates: ModelSpecStageGate[] = [
	{
		modelSpecId: "model-1",
		revision: 3,
		checksum: "checksum-3",
		stage: "RELEASE_READY",
		status: "BLOCKED",
		blockers: [
			{
				code: "MODEL_SPEC_TEST_EVIDENCE_UNKNOWN",
				field: "design",
				message: "当前版本尚无测试产物",
				repairRoute: "/modeling/models/model-1?tab=design",
			},
		],
	},
];

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
});

describe("ModelQualityConstraintPanel", () => {
	it("separates revision-bound model tests from centrally governed data-quality rules", async () => {
		await act(async () => root.render(<ModelQualityConstraintPanel gates={gates} modelName="预算汇总模型" />));

		expect(container.textContent).toContain("模型测试");
		expect(container.textContent).toContain("治理质量规则");
		expect(container.textContent).toContain("规则唯一在数据治理中维护");
		expect(container.textContent).toContain("MODEL_SPEC_TEST_EVIDENCE_UNKNOWN");
		expect(container.textContent).not.toContain("MODEL_SPEC_QUALITY_EVIDENCE_UNKNOWN");
		expect(container.querySelector<HTMLAnchorElement>('a[href="#/governance/rules"]')?.textContent).toContain(
			"进入数据质量中心",
		);
		expect(
			container.querySelector<HTMLAnchorElement>('a[href="#/governance/rules/catalog/new"]')?.textContent,
		).toContain("新建治理质量规则");
	});
});
