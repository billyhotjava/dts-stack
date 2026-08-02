// @vitest-environment jsdom

import { act, type ButtonHTMLAttributes, type PropsWithChildren, type ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AdvancedDbtImplementationView } from "./AdvancedDbtImplementationView";

const api = vi.hoisted(() => ({
	create: vi.fn(),
	save: vi.fn(),
	validate: vi.fn(),
	commit: vi.fn(),
}));

vi.mock("@/api/dbtImplementationDraftApi", () => ({
	createDbtImplementationDraft: api.create,
	saveDbtImplementationDraftFiles: api.save,
	validateDbtImplementationDraft: api.validate,
	commitDbtImplementationDraft: api.commit,
}));

vi.mock("../WorkspacePage", () => ({
	ActionButton: ({ children, ...props }: ButtonHTMLAttributes<HTMLButtonElement>) => (
		<button type="button" {...props}>{children}</button>
	),
	StatusTag: ({ children }: PropsWithChildren) => <span>{children}</span>,
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

const technicalImplementation = {
	implementationId: "implementation-83",
	ownership: "DBT_MANAGED" as const,
	projectKey: "sprint83",
	dbtUniqueId: "model.sprint83.orders",
	inputMode: "IMPORTED",
	inputs: [],
	fieldMappings: [],
	settings: {},
	materialization: "table",
	artifacts: [],
};

const props = {
	modelSpecId: "model-83",
	planId: "plan-83",
	baseModelRevision: 3,
	baseModelChecksum: "a".repeat(64),
	baseImplementationRevision: 2,
	baseImplementationChecksum: "b".repeat(64),
	technicalImplementation,
};

async function render(element: ReactElement) {
	await act(async () => {
		root.render(element);
		await Promise.resolve();
	});
}

const button = (label: string) =>
	Array.from(container.querySelectorAll("button")).find((candidate) => candidate.textContent === label) as HTMLButtonElement;

const click = async (target: HTMLButtonElement) => {
	await act(async () => {
		target.click();
		await Promise.resolve();
		await Promise.resolve();
	});
};

beforeEach(() => {
	vi.clearAllMocks();
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(() => {
	act(() => root.unmount());
	container.remove();
});

describe("AdvancedDbtImplementationView idempotency", () => {
	it("reuses create and commit keys after response loss", async () => {
		const created = {
			draftId: "draft-83",
			etag: "etag-created",
			expiresAt: "2026-08-03T00:00:00Z",
			sourceBundle: {
				projectKey: "sprint83",
				projectChecksum: "c".repeat(64),
				bundleChecksum: "d".repeat(64),
				sourceKind: "CANONICAL_ARTIFACT_RECONSTRUCTION",
				lossless: false,
				files: [{ path: "models/orders.sql", content: "select 1\n", checksum: "e".repeat(64), byteSize: 9 }],
			},
		};
		api.create.mockRejectedValueOnce(new Error("response lost")).mockResolvedValueOnce(created);
		api.save.mockResolvedValue({ draftId: "draft-83", etag: "etag-saved", expiresAt: created.expiresAt });
		api.validate.mockResolvedValue({
			draftId: "draft-83",
			state: "VALIDATED",
			etag: "etag-validated",
			expiresAt: created.expiresAt,
			validatedChecksum: "f".repeat(64),
			diagnostics: [],
			proposedStructure: [],
		});
		api.commit.mockRejectedValueOnce(new Error("response lost")).mockResolvedValueOnce({
			draftId: "draft-83",
			modelSpecId: "model-83",
			modelRevision: 3,
			modelChecksum: props.baseModelChecksum,
			implementationId: "implementation-84",
			implementationRevision: 3,
			implementationChecksum: "1".repeat(64),
			artifactCount: 4,
			etag: "etag-committed",
		});

		await render(<AdvancedDbtImplementationView {...props} />);
		await click(button("开始隔离编辑"));
		await click(button("开始隔离编辑"));

		expect(api.create).toHaveBeenCalledTimes(2);
		expect(api.create.mock.calls[0][1].idempotencyKey).toBe(api.create.mock.calls[1][1].idempotencyKey);

		await click(button("静态校验"));
		expect(button("提交实施修订").disabled).toBe(true);
		const acknowledgement = container.querySelector('input[type="checkbox"]') as HTMLInputElement;
		await act(async () => {
			acknowledgement.click();
			await Promise.resolve();
		});
		expect(button("提交实施修订").disabled).toBe(false);
		await click(button("提交实施修订"));
		await click(button("提交实施修订"));

		expect(api.commit).toHaveBeenCalledTimes(2);
		expect(api.commit.mock.calls[0][2].idempotencyKey).toBe(api.commit.mock.calls[1][2].idempotencyKey);
	});

	it("allows the server to initialize the first implementation", async () => {
		await render(
			<AdvancedDbtImplementationView
				{...props}
				baseImplementationChecksum={null}
				baseImplementationRevision={null}
				technicalImplementation={null}
			/>,
		);

		expect(button("开始隔离编辑")).not.toBeUndefined();
	});
});
