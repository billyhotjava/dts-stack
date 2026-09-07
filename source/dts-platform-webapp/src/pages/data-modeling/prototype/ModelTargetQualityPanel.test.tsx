// @vitest-environment jsdom
import { act, useState } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { ModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import type { UnsavedEditorHandle } from "@/pages/catalog/CatalogDatasetGovernanceSummaryEditor";
import { ModelMaterializationActions } from "./ModelMaterializationActions";
import { ModelTargetQualityPanel } from "./ModelTargetQualityPanel";

const apiMocks = vi.hoisted(() => ({ get: vi.fn(), createRule: vi.fn(), changed: vi.fn(), command: vi.fn() }));
vi.mock("@/api/apiClient", () => ({ default: { get: apiMocks.get } }));
vi.mock("@/api/platformApi", () => ({ createQualityRule: apiMocks.createRule }));

const delivery: ModelDeliveryStatus = {
	workspace: null,
	modelSpecId: "model-1",
	modelRevision: 4,
	modelChecksum: "checksum-4",
	planId: "plan-1",
	environment: "dev",
	candidate: {
		id: "candidate-1",
		version: 11,
		status: "BUILT",
		entryRevision: 4,
		entryChecksum: "checksum-4",
		matchesCurrentModel: true,
		updatedAt: "2026-09-07T00:00:00Z",
	},
	observedAt: "2026-09-07T00:00:00Z",
	recommendedStep: "verification",
	wizard: [],
	steps: [],
	actions: [],
};
const context = {
	candidateId: "candidate-1",
	candidateVersion: 11,
	status: "BUILT",
	assets: [
		{
			modelSpecId: "model-1",
			modelRevision: 4,
			datasetId: "dataset-1",
			assetKey: "source:public.target",
			qualifiedName: "public.target",
			configurable: true,
			rules: [],
		},
	],
};

function Harness({
	canMaintain = true,
	enabled = true,
	busy = "",
}: {
	canMaintain?: boolean;
	enabled?: boolean;
	busy?: string;
}) {
	const [openRequest, setOpenRequest] = useState(0);
	const [guard, setGuard] = useState<UnsavedEditorHandle | null>(null);
	return (
		<MemoryRouter>
			<ModelMaterializationActions
				embedded
				pageAction={{
					code: "CONFIGURE_QUALITY_RULES",
					enabled,
					reasonCode: null,
					targetId: "candidate-1",
					expectedVersion: 11,
				}}
				canMaintain={canMaintain && !guard?.dirty}
				canConfigureQuality={canMaintain}
				busy={busy}
				canBuild={false}
				buildAction={null}
				operationalAction={null}
				modelCount={1}
				onBack={apiMocks.command}
				onPrimaryAction={apiMocks.command}
				onConfigureQuality={() => setOpenRequest((value) => value + 1)}
			/>
			<ModelTargetQualityPanel
				delivery={delivery}
				canMaintain={canMaintain}
				openRequest={openRequest}
				onChanged={apiMocks.changed}
				onNavigationGuardChange={setGuard}
			/>
		</MemoryRouter>
	);
}

let container: HTMLDivElement;
let root: Root;
const scrollDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, "scrollIntoView");
beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	vi.clearAllMocks();
	apiMocks.get.mockResolvedValue(context);
	apiMocks.createRule.mockResolvedValue({ id: "rule-1" });
	Object.defineProperty(HTMLElement.prototype, "scrollIntoView", { configurable: true, value: vi.fn() });
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});
afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
	if (scrollDescriptor) Object.defineProperty(HTMLElement.prototype, "scrollIntoView", scrollDescriptor);
	else Reflect.deleteProperty(HTMLElement.prototype, "scrollIntoView");
	vi.restoreAllMocks();
});
const configureButton = () =>
	container.querySelector<HTMLButtonElement>('button[aria-controls="model-target-quality"]');
const field = (label: string) =>
	container.querySelector<HTMLInputElement | HTMLTextAreaElement>(`[aria-label="${label}"]`);
const changeField = async (label: string, value: string) => {
	const element = field(label);
	if (!element) throw new Error(`Missing field: ${label}`);
	await act(async () => {
		Object.getOwnPropertyDescriptor(Object.getPrototypeOf(element), "value")?.set?.call(element, value);
		element.dispatchEvent(new Event("input", { bubbles: true }));
	});
};

it("opens and focuses the form, preserves unsaved fields on reentry, and publishes to the target dataset", async () => {
	await act(async () => root.render(<Harness />));
	expect(container.querySelector("details")?.open).toBe(false);
	expect(configureButton()?.disabled).toBe(false);
	await act(async () => configureButton()?.click());
	expect(container.querySelector("details")?.open).toBe(true);
	expect(document.activeElement).toBe(field("规则名称"));
	await changeField("规则名称", "目标字段完整性");
	await changeField("检测 SQL", "select count(*) from public.target where id is null");
	expect(configureButton()?.disabled).toBe(false);
	await act(async () => configureButton()?.click());
	expect(field("规则名称")?.value).toBe("目标字段完整性");
	expect(apiMocks.createRule).not.toHaveBeenCalled();
	expect(apiMocks.command).not.toHaveBeenCalled();
	const save = Array.from(container.querySelectorAll("button")).find(
		(button) => button.textContent === "保存并发布规则",
	);
	expect(save?.disabled).toBe(false);
	await act(async () => save?.click());
	expect(apiMocks.createRule).toHaveBeenCalledWith(
		expect.objectContaining({
			datasetId: "dataset-1",
			bindings: [{ datasetId: "dataset-1", scopeType: "DATASET" }],
			name: "目标字段完整性",
			publishNow: true,
		}),
	);
	expect(apiMocks.changed).toHaveBeenCalledTimes(1);
});

it("keeps the editor navigation available during a background status reload", async () => {
	await act(async () => root.render(<Harness busy="load" />));
	expect(configureButton()?.disabled).toBe(false);
	await act(async () => configureButton()?.click());
	expect(container.querySelector("details")?.open).toBe(true);
});

it("keeps permission, server action and active command restrictions", async () => {
	await act(async () => root.render(<Harness canMaintain={false} />));
	expect(configureButton()?.disabled).toBe(true);
	expect(field("规则名称")).toBeNull();
	await act(async () => root.render(<Harness enabled={false} />));
	expect(configureButton()?.disabled).toBe(true);
	await act(async () => root.render(<Harness busy="build" />));
	expect(configureButton()?.disabled).toBe(true);
});
