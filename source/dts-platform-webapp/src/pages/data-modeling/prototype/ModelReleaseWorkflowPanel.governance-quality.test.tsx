// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type {
	ReleaseCandidate,
	ReleaseCandidateEvidenceSummary,
	ReleaseCandidateGovernanceQuality,
} from "@/api/modelSpecApi";
import DIALOG_SOURCE from "./ModelPublishDialog.tsx?raw";
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

	it("reloads the candidate after a quality workflow start failure so recovery actions become visible", () => {
		expect(DIALOG_SOURCE).toMatch(/if \(action === "RUN_QUALITY"\) await load\(\);\s*setFailure\(message\);/);
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
		expect(container.textContent).toContain("治理数据质量记录");
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

	it("opens the authoritative rule catalog without passing an asset key as a dataset UUID", async () => {
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

		expect(container.querySelector<HTMLAnchorElement>('a[href="#/governance/rules/catalog"]')).not.toBeNull();
		expect(container.querySelector('a[href*="datasetId="]')).toBeNull();
	});

	it("shows the authoritative build error code without leaking a generic backend message", async () => {
		const evidence: ReleaseCandidateEvidenceSummary[] = [
			{
				type: "BUILD_RUN",
				state: "FAILED",
				code: "DBT_RUNTIME_NOT_CERTIFIED",
				message: "Current build or physical relation verification failed",
			},
		];

		await act(async () => {
			root.render(
				<ModelReleaseWorkflowPanel
					binding={null}
					candidate={{ ...candidate, status: "BUILD_FAILED" }}
					evidence={evidence}
					governanceQuality={null}
					releaseActions={[]}
				/>,
			);
		});

		expect(container.textContent).toContain("数据构建未通过（错误码 DBT_RUNTIME_NOT_CERTIFIED）");
		expect(container.textContent).not.toContain("Current build or physical relation verification failed");
	});
});
