// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelMaterializationStatusCard, resolveMaterializationPresentation } from "./ModelMaterializationStatus";

const apiMocks = vi.hoisted(() => ({ getStatuses: vi.fn() }));

vi.mock("@/api/modelSpecApi", async (importOriginal) => ({
	...(await importOriginal<typeof import("@/api/modelSpecApi")>()),
	getModelMaterializationStatuses: apiMocks.getStatuses,
}));

const model = {
	id: "model-1",
	planId: "plan-1",
	name: "时间维度表",
	revision: 2,
	compatibilityMode: "CANONICAL",
} as ModelSpecView;

const status = {
	modelSpecId: model.id,
	candidateId: "candidate-1",
	candidateVersion: 6,
	environment: "dev",
	candidateStatus: "BUILT",
	candidateUpdatedAt: "2026-08-09T02:47:00Z",
	currentImplementationRevision: 3,
	evidence: {
		candidateEntryId: "entry-1",
		modelSpecId: model.id,
		modelName: model.name,
		modelRevision: 2,
		implementationRevision: 3,
		targetRelation: "public.it_demo_dwd_dim_date",
		runStatus: "BUILT",
		relationState: "VERIFIED",
		attempt: 2,
		finishedAt: "2026-08-09T02:47:00Z",
		observedAt: "2026-08-09T02:47:01Z",
	},
} as const;

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	apiMocks.getStatuses.mockReset();
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
});

describe("model materialization status", () => {
	it("renders persisted relation evidence and exposes rematerialization", async () => {
		apiMocks.getStatuses.mockResolvedValue([status]);
		const onOpen = vi.fn();

		await act(async () =>
			root.render(
				<ModelMaterializationStatusCard canMaintain currentImplementationRevision={3} model={model} onOpen={onOpen} />,
			),
		);
		await act(async () => Promise.resolve());

		expect(container.textContent).toContain("已构建");
		expect(container.textContent).toContain("public.it_demo_dwd_dim_date");
		expect(container.textContent).toContain("发布单 v6");
		const rebuild = Array.from(container.querySelectorAll("button")).find((item) => item.textContent === "重新构建");
		await act(async () => rebuild?.click());
		expect(onOpen).toHaveBeenCalledOnce();
	});

	it("marks evidence stale when the current implementation pin changed", () => {
		expect(resolveMaterializationPresentation(status, model.revision, 4).key).toBe("STALE");
		expect(resolveMaterializationPresentation({ ...status, currentImplementationRevision: 4 }, model.revision).key).toBe(
			"STALE",
		);
		expect(resolveMaterializationPresentation(null, model.revision, 4).key).toBe("NOT_MATERIALIZED");
	});
});
