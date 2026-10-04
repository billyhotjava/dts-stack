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
		expect(container.textContent).toContain("模型版本交付记录仍需处理");
	});

	it("wraps long release blockers and exposes compact repair links instead of raw routes", async () => {
		const releaseGates = gates.map((gate) =>
			gate.stage === "RELEASE_READY"
				? {
						...gate,
						blockers: [
							...gate.blockers,
							{
								code: "MODEL_SPEC_STANDARD_EVIDENCE_STALE",
								field: "standards",
								message: "发布策略要求多个字段绑定标准，修复信息必须在弹窗中完整换行显示",
								repairRoute: "/modeling/models/model-1?tab=standards",
							},
							{
								code: "CLASSIFICATION_INPUT_REQUIRED",
								field: "fields",
								message: "至少需要一项已确认的上游或字段分类分级输入",
								repairRoute: "/modeling/models/model-1?tab=governance",
							},
						],
					}
				: gate,
		);

		await act(async () => root.render(<ModelStageGatePanel gates={releaseGates} targetStage="RELEASE_READY" />));

		expect(container.querySelectorAll(".dmx-stage-gate-blocker")).toHaveLength(3);
		expect(container.querySelector(".dmx-stage-gate-table .ant-table-cell-ellipsis")).toBeNull();
		const repairLinks = Array.from(container.querySelectorAll<HTMLAnchorElement>(".dmx-stage-gate-repairs a"));
		expect(repairLinks).toHaveLength(2);
		expect(repairLinks.map((link) => link.getAttribute("href"))).toEqual([
			"#/modeling/models/model-1?tab=governance",
			"#/modeling/models/model-1?tab=standards",
		]);
		expect(container.textContent).not.toContain("/modeling/models/model-1?tab=governance");
	});
});
