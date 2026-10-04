// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter, useLocation } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import HelpCenter from "./HelpCenter";
import { getHelpTopicById } from "./helpTopics";

function EditorProbe() {
	const location = useLocation();
	return (
		<>
			<input aria-label="未保存模型名称" defaultValue="保留我的修改" />
			<output data-testid="route">
				{location.pathname}
				{location.search}
			</output>
			<HelpCenter />
		</>
	);
}

let host: HTMLDivElement;
let root: Root;
const button = (name: string) => {
	const found = [...document.querySelectorAll<HTMLButtonElement>("button")].find(
		(item) => item.getAttribute("aria-label") === name || item.textContent?.trim() === name,
	);
	if (!found) throw new Error(`Missing button: ${name}`);
	return found;
};
const click = async (name: string) => {
	await act(async () => {
		button(name).click();
	});
};
const mount = async (url: string) => {
	await act(async () => {
		root.render(
			<MemoryRouter initialEntries={[url]}>
				<EditorProbe />
			</MemoryRouter>,
		);
	});
};

beforeEach(() => {
	vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true);
	vi.stubGlobal(
		"ResizeObserver",
		class {
			observe() {}
			unobserve() {}
			disconnect() {}
		},
	);
	host = document.createElement("div");
	document.body.appendChild(host);
	root = createRoot(host);
});
afterEach(async () => {
	await act(async () => {
		root.unmount();
	});
	host.remove();
	vi.unstubAllGlobals();
});

describe("contextual model help", () => {
	it("opens implementation guidance inside legacy help without leaving or clearing the editor", async () => {
		const url = "/data-modeling/dimensions/workbench?modelSpecId=existing-model";
		await mount(url);
		await click("打开帮助");
		expect(document.querySelector('[role="dialog"]')?.textContent).toContain(getHelpTopicById("model-center")?.title);
		await click(getHelpTopicById("model-implementation")!.title);
		expect(document.querySelector('[role="dialog"]')?.textContent).toContain("普通明细");
		expect(document.querySelector('[data-testid="route"]')?.textContent).toBe(url);
		expect(host.querySelector<HTMLInputElement>("input")?.value).toBe("保留我的修改");
		await click("返回当前页面帮助");
		expect(document.querySelector('[role="dialog"]')?.textContent).toContain(getHelpTopicById("model-center")?.title);
		await click("Close");
		await act(async () => {
			await new Promise((resolve) => setTimeout(resolve, 0));
		});
		expect(document.querySelector('[role="dialog"]')).toBeNull();
		expect(document.activeElement).toBe(button("打开帮助"));
		expect(host.querySelector<HTMLInputElement>("input")?.value).toBe("保留我的修改");
	});

	it("uses the step topic and resets manual browsing when the drawer is reopened", async () => {
		await mount("/data-modeling/dimensions/workbench?step=implementation");
		await click("打开帮助");
		expect(document.querySelector('[role="dialog"]')?.textContent).toContain(
			getHelpTopicById("model-implementation")?.title,
		);
		await click(getHelpTopicById("model-center")!.title);
		await click("Close");
		await click("打开帮助");
		expect(document.querySelector('[role="dialog"] h2')?.textContent).toBe(
			getHelpTopicById("model-implementation")?.title,
		);
	});
});
