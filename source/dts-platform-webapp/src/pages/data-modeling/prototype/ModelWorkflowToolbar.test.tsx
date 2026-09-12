// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ComponentProps } from "react";
import { ModelWorkflowToolbar } from "./ModelWorkflowToolbar";
import { ModelAuthoringDiagnostics } from "./ModelAuthoringDiagnostics";
import type { ModelAuthoringContext, ModelAuthoringValidation } from "@/api/modelAuthoringApi";

type Props = ComponentProps<typeof ModelWorkflowToolbar>;
let root: Root;
let container: HTMLDivElement;
const context = {
	allowedActions: ["SAVE", "VALIDATE", "COMMIT"],
	openDraft: { draftId: "draft", state: "VALIDATED", etag: "etag" },
} as ModelAuthoringContext;
const validation: ModelAuthoringValidation = {
	modelIssues: [],
	projectionIssues: [],
	implementationValidation: {
		draftId: "draft",
		state: "VALIDATED",
		etag: "etag",
		expiresAt: "2099-01-01T00:00:00Z",
		validatedChecksum: "checksum",
		diagnostics: [],
		proposedStructure: [],
	},
};
const props = (patch: Partial<Props> = {}): Props => ({
	persisted: true,
	published: false,
	dirty: false,
	busy: false,
	saving: false,
	readOnly: false,
	canMaintain: true,
	authoringBusy: "",
	authoringContext: context,
	authoringValidation: validation,
	onSave: vi.fn(),
	onValidate: vi.fn(),
	onCommit: vi.fn(),
	onForkPublished: vi.fn(),
	onRefresh: vi.fn(),
	onDialog: vi.fn(),
	...patch,
});
const render = async (p: Props) => {
	await act(async () => root.render(<ModelWorkflowToolbar {...p} />));
};
const primary = () => container.querySelector<HTMLButtonElement>('[aria-label="模型主流程操作"] button')!;
beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});
afterEach(() => {
	act(() => root.unmount());
	container.remove();
	vi.restoreAllMocks();
});
describe("single primary authoring action", () => {
	it("offers only save for dirty content and invokes no later command", async () => {
		const p = props({ dirty: true });
		await render(p);
		expect(container.querySelectorAll('[aria-label="模型主流程操作"] button')).toHaveLength(1);
		expect(primary().textContent).toBe("保存草稿");
		act(() => primary().click());
		expect(p.onSave).toHaveBeenCalledTimes(1);
		expect(p.onCommit).not.toHaveBeenCalled();
		expect(p.onDialog).not.toHaveBeenCalled();
		expect(container.textContent).not.toContain("按顺序完成");
	});
	it.each([null, context])(
		"blocks secondary delivery when context is absent or a draft is pending: %s",
		async (authoringContext) => {
			const p = props({ authoringContext });
			await render(p);
			const delivery = Array.from(container.querySelectorAll("button")).find(
				(item) => item.textContent === "构建与交付",
			)!;
			expect(delivery.disabled).toBe(true);
			act(() => delivery.click());
			expect(p.onDialog).not.toHaveBeenCalled();
		},
	);

	it("uses the latest rendered permission and prevents duplicate commands while busy", async () => {
		const p = props();
		await render(p);
		expect(primary().textContent).toBe("提交加工配置");
		act(() => primary().click());
		expect(p.onCommit).toHaveBeenCalledTimes(1);
		await render({ ...p, busy: true });
		act(() => primary().click());
		expect(p.onCommit).toHaveBeenCalledTimes(1);
		await render({ ...p, authoringContext: { ...context, allowedActions: [] } });
		expect(primary().disabled).toBe(true);
	});
	it("requires renewed validation when the receipt expires before a click", async () => {
		const p = props();
		await render(p);
		vi.spyOn(Date, "now").mockReturnValue(Date.parse("2100-01-01T00:00:00Z"));
		act(() => primary().click());
		expect(p.onCommit).not.toHaveBeenCalled();
		expect(primary().textContent?.replace(/\s/g, "")).toBe("校验");
	});
	it("opens delivery without implicitly publishing or rebuilding", async () => {
		const p = props({
			authoringContext: {
				...context,
				openDraft: null,
				implementation: {} as NonNullable<ModelAuthoringContext["implementation"]>,
			},
		});
		await render(p);
		expect(primary().textContent).toBe("构建与交付");
		act(() => primary().click());
		expect(p.onDialog).toHaveBeenCalledWith("publish");
		expect(p.onCommit).not.toHaveBeenCalled();
		expect(p.onValidate).not.toHaveBeenCalled();
	});
	it("shows implementation and projection errors instead of silently hiding them", async () => {
		const issue = { code: "INVALID_SQL", severity: "ERROR", path: "models/example.sql", message: "SQL 字段不存在" };
		await act(async () =>
			root.render(<ModelAuthoringDiagnostics validation={{ ...validation, projectionIssues: [issue] }} />),
		);
		expect(container.querySelector('[role="alert"]')?.textContent).toContain("SQL 字段不存在");
	});
});
