// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeAll, beforeEach, describe, expect, it } from "vitest";
import type { ModelSpecStageGate } from "@/api/modelSpecApi";
import { ModelStageGatePanel } from "./ModelStageGatePanel";

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
		revision: 2,
		checksum: "checksum-2",
		stage: "DRAFT_SAVE",
		status: "READY",
		blockers: [],
	},
	{
		modelSpecId: "model-1",
		revision: 2,
		checksum: "checksum-2",
		stage: "DESIGNED",
		status: "READY",
		blockers: [],
	},
	{
		modelSpecId: "model-1",
		revision: 2,
		checksum: "checksum-2",
		stage: "IMPLEMENTATION_READY",
		status: "READY",
		blockers: [],
	},
	{
		modelSpecId: "model-1",
		revision: 2,
		checksum: "checksum-2",
		stage: "RELEASE_READY",
		status: "BLOCKED",
		blockers: [
			{
				code: "MODEL_SPEC_PERMISSION_EVIDENCE_STALE",
				field: "fields",
				message: "字段权限分级未完成",
				repairRoute: "/modeling/models/model-1?tab=governance",
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

describe("ModelStageGatePanel", () => {
	it("treats a ready DESIGNED gate as a successful submit check without showing future release blockers", async () => {
		await act(async () => root.render(<ModelStageGatePanel gates={gates} targetStage="DESIGNED" />));

		const rows = container.querySelectorAll("tbody tr.ant-table-row");
		expect(rows).toHaveLength(1);
		expect(rows[0]?.textContent).toContain("DESIGNED");
		expect(rows[0]?.textContent).toContain("READY");
		expect(container.textContent).toContain("逻辑设计提交检查已通过");
		expect(container.textContent).not.toContain("MODEL_SPEC_PERMISSION_EVIDENCE_STALE");
	});

	it("keeps release evidence blockers visible in the release gate view", async () => {
		await act(async () => root.render(<ModelStageGatePanel gates={gates} targetStage="RELEASE_READY" />));

		const rows = container.querySelectorAll("tbody tr.ant-table-row");
		expect(rows).toHaveLength(1);
		expect(rows[0]?.textContent).toContain("RELEASE_READY");
		expect(rows[0]?.textContent).toContain("MODEL_SPEC_PERMISSION_EVIDENCE_STALE");
		expect(container.textContent).toContain("发布前证据仍需处理");
	});
});
