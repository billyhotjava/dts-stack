// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { DbtDraftFile } from "@/api/dbtImplementationDraftApi";
import type { ModelAuthoringContext } from "@/api/modelAuthoringApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { AdvancedDbtWorkspace, type AdvancedDbtWorkspaceProps } from "./AdvancedDbtWorkspace";

vi.mock("./DbtCodeEditor", () => ({
	DbtCodeEditor: ({
		path,
		content,
		readOnly,
		onChange,
	}: {
		path: string;
		content: string;
		readOnly: boolean;
		onChange: (content: string) => void;
	}) => (
		<textarea
			aria-label={`编辑 ${path}`}
			disabled={readOnly}
			onInput={(event) => onChange(event.currentTarget.value)}
			value={content}
		/>
	),
}));

const model = {
	id: "10000000-0000-0000-0000-000000000092",
	name: "项目明细",
	status: "DRAFT",
	revision: 3,
	checksum: "a".repeat(64),
	implementationMode: "DBT_MANAGED",
} as ModelSpecView;

const files: DbtDraftFile[] = [
	{ path: "models/.dts_dependencies/source.sql", content: "select 1" },
	{ path: "models/project.sql", content: "select project_id" },
];

const context = (patch: Partial<ModelAuthoringContext> = {}): ModelAuthoringContext => ({
	model,
	implementation: null,
	provenance: { origin: "DBT_ZIP_IMPORT", sourceKind: "FROZEN_SOURCE_BUNDLE", lossless: true },
	projection: {
		coverage: "PARTIAL",
		lossless: false,
		managedPaths: ["models/project.sql"],
		rawNodes: [{ nodeId: "raw-1", kind: "RAW_SQL", editable: true, sourcePath: "models/project.sql", line: 1 }],
		reasons: [],
	},
	openDraft: {
		draftId: "draft-92",
		planId: "plan-92",
		modelSpecId: model.id,
		baseModelRevision: 3,
		baseModelChecksum: model.checksum,
		state: "DRAFT",
		etag: "etag-1",
		expiresAt: "2026-09-01T00:00:00Z",
		sourceBundle: null,
	},
	allowedActions: ["OPEN_VISUAL", "OPEN_CODE", "EDIT_MODEL", "EDIT_IMPLEMENTATION", "SAVE", "VALIDATE", "COMMIT"],
	publishedForkRequired: false,
	...patch,
});

const props = (patch: Partial<AdvancedDbtWorkspaceProps> = {}): AdvancedDbtWorkspaceProps => ({
	model,
	context: context(),
	files,
	validation: null,
	commit: null,
	dirty: false,
	busy: "",
	conflict: false,
	failure: "",
	canMaintain: true,
	onBack: vi.fn(),
	onCreate: vi.fn(),
	onFilesChange: vi.fn(),
	onSave: vi.fn(),
	onValidate: vi.fn(),
	onCommit: vi.fn(),
	...patch,
});

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

const render = async (value: AdvancedDbtWorkspaceProps) =>
	act(async () => root.render(<AdvancedDbtWorkspace {...value} />));

describe("AdvancedDbtWorkspace unified authoring view", () => {
	it("returns from code view to visual mode", async () => {
		const value = props();
		await render(value);
		const backButton = Array.from(container.querySelectorAll("button")).find(
			(item) => item.textContent === "返回可视化模式",
		);
		expect(backButton).toBeDefined();
		act(() => backButton?.click());
		expect(value.onBack).toHaveBeenCalledTimes(1);
	});

	it("edits the shared file state without calling a second API owner", async () => {
		const value = props({ dirty: true });
		await render(value);
		const fileButton = Array.from(container.querySelectorAll("button")).find((item) =>
			item.textContent?.includes("models/project.sql"),
		);
		act(() => fileButton?.click());
		const editor = container.querySelector<HTMLTextAreaElement>('textarea[aria-label="编辑 models/project.sql"]');
		expect(editor?.disabled).toBe(false);
		act(() => {
			if (!editor) return;
			const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, "value")?.set;
			setter?.call(editor, "select project_id, project_name");
			editor.dispatchEvent(new Event("input", { bubbles: true }));
		});
		expect(value.onFilesChange).toHaveBeenCalledWith([
			files[0],
			{ path: "models/project.sql", content: "select project_id, project_name" },
		]);
	});

	it("keeps system dependencies immutable and uses the shared lifecycle callbacks", async () => {
		const value = props({
			dirty: true,
			validation: {
				modelIssues: [],
				projectionIssues: [],
				implementationValidation: {
					draftId: "draft-92",
					state: "VALIDATED",
					etag: "etag-2",
					expiresAt: "2026-09-01T00:00:00Z",
					validatedChecksum: "b".repeat(64),
					diagnostics: [],
					proposedStructure: [],
				},
			},
		});
		await render(value);
		const dependencyButton = Array.from(container.querySelectorAll("button")).find((item) =>
			item.textContent?.includes("系统依赖"),
		);
		act(() => dependencyButton?.click());
		expect(
			container.querySelector<HTMLTextAreaElement>('textarea[aria-label="编辑 models/.dts_dependencies/source.sql"]')
				?.disabled,
		).toBe(true);
		const byText = (text: string) =>
			Array.from(container.querySelectorAll("button")).find((item) => item.textContent?.replace(/\s/g, "") === text);
		act(() => byText("保存草稿")?.click());
		act(() => byText("校验")?.click());
		expect(value.onSave).toHaveBeenCalledTimes(1);
		expect(value.onValidate).toHaveBeenCalledTimes(1);
		expect(byText("提交加工配置")?.hasAttribute("disabled")).toBe(true);
	});

	it("shows one published fork action and never exposes ownership takeover", async () => {
		const value = props({
			context: context({
				openDraft: null,
				publishedForkRequired: true,
				allowedActions: ["OPEN_VISUAL", "OPEN_CODE", "FORK_DRAFT"],
			}),
			files: [],
		});
		await render(value);
		expect(container.textContent).toContain("创建新草稿版本");
		expect(container.textContent).not.toContain("接管代码实现");
		expect(container.textContent).not.toContain("所有权");
	});

	it("shows projection diagnostics and blocks commit while the shared model snapshot is invalid", async () => {
		const value = props({
			validation: {
				modelIssues: [
					{ code: "MODEL_SPEC_NAME_REQUIRED", field: "name", severity: "ERROR", message: "请填写模型名称" },
				],
				projectionIssues: [
					{
						code: "MODEL_AUTHORING_PROJECTION_ALIAS_UNAVAILABLE",
						severity: "WARNING",
						message: "请在代码视图检查表达式",
					},
				],
				implementationValidation: {
					draftId: "draft-92",
					state: "VALIDATED",
					etag: "etag-2",
					expiresAt: "2026-09-01T00:00:00Z",
					validatedChecksum: "b".repeat(64),
					diagnostics: [],
					proposedStructure: [],
				},
			},
		});
		await render(value);

		expect(container.textContent).toContain("请填写模型名称");
		expect(container.textContent).toContain("MODEL_AUTHORING_PROJECTION_ALIAS_UNAVAILABLE");
		const commit = Array.from(container.querySelectorAll("button")).find((item) =>
			item.textContent?.includes("提交加工配置"),
		);
		expect(commit?.hasAttribute("disabled")).toBe(true);
	});
});
