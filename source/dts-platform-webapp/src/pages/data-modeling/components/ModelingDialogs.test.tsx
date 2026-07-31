// @vitest-environment jsdom

import { act, type ReactElement, useState } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { type ModelingDialogKind, ModelingDialogs } from "./ModelingDialogs";

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

function Harness() {
	const [dialog, setDialog] = useState<ModelingDialogKind>(null);

	return (
		<>
			<button id="dialog-trigger" onClick={() => setDialog("display")} type="button">
				打开字段显示设置
			</button>
			<ModelingDialogs
				dialog={dialog}
				onClose={() => setDialog(null)}
				onToggleColumn={() => undefined}
				rows={[]}
				selectionCode="dim_budget_account"
				visibleColumns={new Set(["sequence", "code"])}
			/>
		</>
	);
}

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

describe("ModelingDialogs accessibility", () => {
	it("moves focus into the modal, closes with Escape, and restores focus", async () => {
		await render(<Harness />);
		const trigger = container.querySelector("#dialog-trigger") as HTMLButtonElement;
		trigger.focus();

		await act(async () => {
			trigger.click();
			await Promise.resolve();
		});

		const dialog = document.querySelector('[role="dialog"]') as HTMLElement;
		expect(dialog).not.toBeNull();
		expect(dialog.contains(document.activeElement)).toBe(true);

		await act(async () => {
			document.activeElement?.dispatchEvent(new KeyboardEvent("keydown", { bubbles: true, key: "Escape" }));
			await Promise.resolve();
		});

		expect(document.querySelector('[role="dialog"]')).toBeNull();
		expect(document.activeElement).toBe(trigger);
	});
});
