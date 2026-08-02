// @vitest-environment jsdom

import { act, type ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { StandardPackageImportDialog, StandardsEditorDialog } from "./StandardsDialogs";

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

async function render(element: ReactElement) {
	await act(async () => {
		root.render(element);
		await Promise.resolve();
	});
}

beforeEach(() => {
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(() => {
	act(() => root.unmount());
	container.remove();
});

describe("standards dialogs", () => {
	it("shows a truthful permission-disabled editor state", async () => {
		await render(
			<StandardsEditorDialog
				canManage={false}
				onClose={() => undefined}
				onSaved={() => undefined}
				row={null}
				view="fields"
			/>,
		);

		expect(document.body.textContent).toContain("当前账号仅可查看，不能维护标准");
		expect((document.querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBe(true);
	});

	it("requires a zip and a preview before applying a standard package", async () => {
		await render(<StandardPackageImportDialog canManage onApplied={() => undefined} onClose={() => undefined} />);

		expect(document.body.textContent).toContain("必须先预检");
		expect((document.querySelector('input[type="file"]') as HTMLInputElement).accept).toContain(".zip");
		const applyButton = Array.from(document.querySelectorAll("button")).find((button) =>
			button.textContent?.includes("确认应用"),
		);
		expect(applyButton?.disabled).toBe(true);
	});

	it("does not offer inputs that the selected canonical owner cannot persist", async () => {
		await render(
			<StandardsEditorDialog canManage onClose={() => undefined} onSaved={() => undefined} row={null} view="codes" />,
		);
		expect(document.querySelector("#dm-standard-scope")).not.toBeNull();
		expect(document.querySelector("#dm-standard-definition")).toBeNull();

		await render(
			<StandardsEditorDialog
				canManage
				onClose={() => undefined}
				onSaved={() => undefined}
				row={null}
				view="dictionary"
			/>,
		);
		expect(document.querySelector("#dm-standard-scope")).toBeNull();
		expect(document.querySelector("#dm-standard-definition")).not.toBeNull();
	});
});
