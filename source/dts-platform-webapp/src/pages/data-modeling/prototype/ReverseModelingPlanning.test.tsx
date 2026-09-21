// @vitest-environment jsdom
import { act, useState } from "react";
import { createRoot } from "react-dom/client";
import { expect, it } from "vitest";
import { readFileSync, writeFileSync } from "node:fs";
import {
	ConfirmStep,
	applyModelSecurityLevel,
	previewReadinessIssues,
} from "./ReverseModelingInspectionSteps";
import type { ModelSpecImportSemanticOverride } from "@/api/modelSpecImportApi";

(
	globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;
window.matchMedia = ((media: string) => ({
	matches: false,
	media,
	addListener() {},
	removeListener() {},
	addEventListener() {},
	removeEventListener() {},
})) as typeof window.matchMedia;

it("retains business process and both application mappings and clears preview blockers", async () => {
	const models = ["FACT", "APPLICATION"].map((modelType) => ({
		dbtUniqueId: `model.pjm.${modelType}`,
		name: modelType,
		columns: [{ name: "id", dataType: "text" }],
		semantics: { modelType, domainCode: "PRJ" },
		conversion: { mode: "DBT_BACKED", reasonCodes: [] },
	}));
	const noop = () => {};
	const props = {
		inspection: {
			package: { models, sources: [], dbt: { manifestVersion: "v12" } },
			compatibility: {
				inspection: "SUPPORTED",
				importProjection: "IMPORTABLE",
				issues: [],
			},
		},
		plans: [],
		planId: "plan",
		plansLoading: false,
		onPlanId: noop,
		selected: models.map((m) => m.dbtUniqueId),
		onSelected: noop,
		domains: [],
		domainMappings: { PRJ: "domain" },
		onDomainMapping: noop,
		sources: [],
		sourceMappings: {},
		onSourceMapping: noop,
		businessProcesses: [
			{
				id: "process",
				processId: "prj_mgmt",
				name: "项目管理业务",
				domainId: "domain",
			},
		],
		dataMarts: [
			{ id: "mart", name: "项目管理数据集市", code: "pjm" },
			{ id: "empty", name: "无主题域集市" },
		],
		subjectDomains: [
			{
				id: "subject",
				martId: "mart",
				name: "项目管理主题域",
				code: "project",
			},
		],
		renameMappings: [],
		onRenameMappings: noop,
	} as unknown as Omit<
		Parameters<typeof ConfirmStep>[0],
		"semanticOverrides" | "onSemanticOverride"
	>;
	let current: Record<string, ModelSpecImportSemanticOverride> =
		Object.fromEntries(
			models.map((m) => [
				m.dbtUniqueId,
				applyModelSecurityLevel(
					{ modelUniqueId: m.dbtUniqueId },
					["id"],
					"SECRET",
				),
			]),
		);
	function Harness() {
		const [overrides, setOverrides] = useState(current);
		current = overrides;
		return (
			<ConfirmStep
				{...props}
				semanticOverrides={overrides}
				onSemanticOverride={(id, value) =>
					setOverrides((previous) => ({ ...previous, [id]: value }))
				}
			/>
		);
	}
	const host = document.createElement("div");
	document.body.append(host);
	const root = createRoot(host);
	const select = (label: string) =>
		host.querySelector(`[aria-label="${label}"]`) as HTMLSelectElement;
	const change = async (label: string, value: string) => {
		await act(async () => {
			select(label).value = value;
			select(label).dispatchEvent(new Event("change", { bubbles: true }));
		});
		expect(select(label).value).toBe(value);
	};
	const issues = () =>
		previewReadinessIssues({
			...props,
			packageDomains: ["PRJ"],
			semanticOverrides: current,
		});
	try {
		await act(async () => root.render(<Harness />));
		expect(issues()).toHaveLength(2);
		await change("model.pjm.FACT 业务过程", "process");
		await change("model.pjm.APPLICATION 数据集市", "mart");
		expect(select("model.pjm.APPLICATION 主题域").disabled).toBe(false);
		await change("model.pjm.APPLICATION 主题域", "subject");
		expect(current["model.pjm.FACT"].businessProcessId).toBe("process");
		expect(issues()).toEqual([]);
		for (const field of host.querySelectorAll(
			".dmx-import-planning-field select",
		)) {
			expect(
				field.closest("td")?.classList.contains("ant-table-cell-ellipsis"),
			).toBe(false);
		}
		expect(host.querySelectorAll(".dmx-import-planning-field")).toHaveLength(3);
		if (process.env.PLANNING_LAYOUT_EVIDENCE) {
			const css = readFileSync(
				new URL("../data-modeling.css", import.meta.url),
				"utf8",
			);
			writeFileSync(
				process.env.PLANNING_LAYOUT_EVIDENCE,
				`<html><head>${document.head.innerHTML}<style>${css}\nbody{margin:16px}.dts-compact-table td{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:320px}</style></head><body>${host.innerHTML}</body></html>`,
			);
		}
		await change("model.pjm.APPLICATION 数据集市", "empty");
		expect(current["model.pjm.APPLICATION"].subjectDomainId).toBeUndefined();
		expect(host.textContent).toContain("该数据集市无已确认主题域");
		expect(issues()).toHaveLength(1);
	} finally {
		await act(async () => root.unmount());
		host.remove();
	}
});
