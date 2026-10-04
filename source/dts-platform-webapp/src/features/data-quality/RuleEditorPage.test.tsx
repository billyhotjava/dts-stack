// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
	createQualityRule: vi.fn(),
	listQualityRules: vi.fn(),
	listQualityTemplates: vi.fn(),
	previewTemplateSQL: vi.fn(),
	updateQualityRule: vi.fn(),
}));
vi.mock("@/api/platformApi", () => api);
vi.mock("./useDefaultLakeDatasets", () => ({
	useDefaultLakeDatasets: () => ({
		datasets: [{ id: "dataset-1", name: "目标表", schemaName: "dwd", tableName: "orders" }],
		loading: false,
		message: "",
		lakeName: "默认数据湖",
	}),
}));
vi.mock("./useQualityAccess", () => ({ useQualityMaintainerAccess: () => true }));

import { RuleEditorPage } from "./RuleEditorPage";

let root: Root;
let container: HTMLDivElement;
const rule = {
	id: "rule-1",
	name: "订单规则",
	type: "COMPLETENESS",
	severity: "MEDIUM",
	datasetId: "dataset-1",
	enabled: true,
	latestVersion: {
		id: "version-8",
		ruleId: "rule-1",
		version: 8,
		status: "PUBLISHED",
		definition: '{"sql":"select 1"}',
	},
};
const Location = () => <output data-testid="location">{useLocation().pathname + useLocation().search}</output>;
const render = async (entry: string) => {
	await act(async () =>
		root.render(
			<MemoryRouter initialEntries={[entry]}>
				<Routes>
					<Route
						path="/governance/rules/catalog/:ruleId/edit"
						element={
							<>
								<RuleEditorPage />
								<Location />
							</>
						}
					/>
					<Route path="*" element={<Location />} />
				</Routes>
			</MemoryRouter>,
		),
	);
};
const flush = async () => act(async () => Promise.resolve());

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
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
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	api.listQualityRules.mockResolvedValue([rule]);
	api.updateQualityRule.mockResolvedValue({ ...rule, latestVersion: { ...rule.latestVersion, version: 9 } });
});
afterEach(() => {
	act(() => root.unmount());
	container.remove();
	vi.restoreAllMocks();
});

describe("model-bound RuleEditor", () => {
	it("uses the fixed model dataset and loaded version, then returns only to a safe internal route", async () => {
		const returnTo =
			"/data-modeling/dimensions/workbench?modelSpecId=model-1&candidateId=candidate-1&environment=prod&step=verification";
		await render(`/governance/rules/catalog/rule-1/edit?datasetId=dataset-1&returnTo=${encodeURIComponent(returnTo)}`);
		await flush();
		const save = Array.from(container.querySelectorAll("button")).find(
			(item) => item.textContent === "保存规则",
		) as HTMLButtonElement;
		await act(async () => save.click());
		await flush();
		expect(api.updateQualityRule).toHaveBeenCalledWith(
			"rule-1",
			expect.objectContaining({ datasetId: "dataset-1", expectedVersion: 8 }),
		);
		expect(container.querySelector('[data-testid="location"]')?.textContent).toBe(returnTo);
	});

	it("rejects an external returnTo and keeps navigation inside the quality workspace", async () => {
		await render("/governance/rules/catalog/rule-1/edit?datasetId=dataset-1&returnTo=https%3A%2F%2Fevil.example");
		await flush();
		const save = Array.from(container.querySelectorAll("button")).find(
			(item) => item.textContent === "保存规则",
		) as HTMLButtonElement;
		await act(async () => save.click());
		await flush();
		expect(container.querySelector('[data-testid="location"]')?.textContent).toBe("/governance/rules/catalog/rule-1");
	});
});
