// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReleaseCandidate, ReleaseCandidateGovernanceQuality } from "@/api/modelSpecApi";
import { ModelReleaseWorkflowPanel } from "./ModelReleaseWorkflowPanel";

const candidate = {
	id: "20000000-0000-0000-0000-000000000001",
	status: "QUALITY_RUNNING",
	version: 4,
	audit: {},
} as ReleaseCandidate;

const failedQuality = {
	required: true,
	state: "FAILED",
	code: "MODEL_SPEC_GOVERNANCE_QUALITY_FAILED",
	message: "治理数据质量检查未通过",
	maxAgeSeconds: 3600,
	evidence: [
		{
			assetKey: "source:lake/schema:dwd/table:budget",
			ruleId: "30000000-0000-0000-0000-000000000001",
			ruleVersionId: "40000000-0000-0000-0000-000000000001",
			bindingId: "50000000-0000-0000-0000-000000000001",
			runId: "60000000-0000-0000-0000-000000000001",
			status: "FAILED",
			evidenceChecksum: "a".repeat(64),
			violations: ["FAILED"],
		},
	],
} satisfies ReleaseCandidateGovernanceQuality;

describe("ModelReleaseWorkflowPanel governance quality rerun", () => {
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

	it("offers a distinct rerun action for failed bound governance evidence", async () => {
		const rerun = vi.fn();
		await act(async () => {
			root.render(
				<ModelReleaseWorkflowPanel
					binding={null}
					candidate={candidate}
					evidence={[]}
					governanceQuality={failedQuality}
					governanceQualityRerunning={false}
					onRerunGovernanceQuality={rerun}
					releaseActions={[]}
				/>,
			);
		});

		const button = Array.from(container.querySelectorAll("button")).find((item) =>
			item.textContent?.includes("重新运行治理质量"),
		);
		expect(button).toBeTruthy();
		await act(async () => button?.click());
		expect(rerun).toHaveBeenCalledTimes(1);
		expect(container.textContent).toContain("工程验证");
		expect(container.textContent).toContain("治理数据质量证据");
	});

	it("keeps unbound missing evidence on the configuration path", async () => {
		await act(async () => {
			root.render(
				<ModelReleaseWorkflowPanel
					binding={null}
					candidate={candidate}
					evidence={[]}
					governanceQuality={{
						...failedQuality,
						evidence: [
							{
								assetKey: "source:lake/schema:dwd/table:budget",
								status: "MISSING",
								violations: ["MISSING"],
							},
						],
					}}
					onRerunGovernanceQuality={vi.fn()}
					releaseActions={[]}
				/>,
			);
		});

		expect(container.textContent).not.toContain("重新运行治理质量");
		expect(container.textContent).toContain("配置质量规则");
	});
});
