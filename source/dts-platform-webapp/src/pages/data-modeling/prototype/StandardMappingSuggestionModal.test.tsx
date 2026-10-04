// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { StandardMappingSuggestionModal } from "./StandardMappingSuggestionModal";

const serviceMocks = vi.hoisted(() => ({
	preview: vi.fn(),
	apply: vi.fn(),
}));

vi.mock("./services/standardsProjectionService", () => ({
	previewSuggestedStandardMappings: serviceMocks.preview,
	applySuggestedStandardMappings: serviceMocks.apply,
}));

const suggestion = {
	id: "model-1:payable_amount:standard-1@2",
	modelId: "model-1",
	modelName: "应付账款事实",
	modelStatus: "PUBLISHED" as const,
	modelRevision: 3,
	modelChecksum: "checksum",
	fieldName: "payable_amount",
	fieldLabel: "应付账款金额",
	fieldDataType: "DECIMAL",
	standardId: "standard-1",
	standardCode: "payable_amount",
	standardName: "应付账款金额",
	standardVersion: 2,
	createsDraft: true,
};

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	Object.defineProperty(window, "matchMedia", {
		writable: true,
		value: vi.fn().mockImplementation((query: string) => ({
			matches: false,
			media: query,
			onchange: null,
			addListener: vi.fn(),
			removeListener: vi.fn(),
			addEventListener: vi.fn(),
			removeEventListener: vi.fn(),
			dispatchEvent: vi.fn(),
		})),
	});
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	serviceMocks.preview.mockResolvedValue([suggestion]);
	serviceMocks.apply.mockResolvedValue([{ modelId: "model-1", modelName: "应付账款事实", success: true, revision: 4 }]);
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
	vi.clearAllMocks();
});

it("previews selected candidates and applies only after explicit confirmation", async () => {
	const onComplete = vi.fn();
	await act(async () => root.render(<StandardMappingSuggestionModal onClose={vi.fn()} onComplete={onComplete} />));
	await act(async () => undefined);

	expect(container.textContent).toContain("应付账款事实");
	expect(container.textContent).toContain("应付账款金额");
	expect(container.textContent).toContain("将创建新草稿");
	const confirm = Array.from(container.querySelectorAll("button")).find((button) =>
		button.textContent?.includes("确认补全（1）"),
	);
	expect(confirm).toBeDefined();

	await act(async () => confirm?.click());

	expect(serviceMocks.apply).toHaveBeenCalledWith([suggestion]);
	expect(onComplete).toHaveBeenCalledWith(1, 0);
});
