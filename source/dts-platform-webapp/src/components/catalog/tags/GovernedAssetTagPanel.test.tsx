// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { getAssetTagCapability, listAssetTags, listAllEnabledCatalogTags, listTagCategories, tagAsset, untagAsset } =
	vi.hoisted(() => ({
		getAssetTagCapability: vi.fn(),
		listAssetTags: vi.fn(),
		listAllEnabledCatalogTags: vi.fn(),
		listTagCategories: vi.fn(),
		tagAsset: vi.fn(),
		untagAsset: vi.fn(),
	}));

vi.mock("@/api/catalogTagsApi", () => ({
	getAssetTagCapability,
	listAssetTags,
	listAllEnabledCatalogTags,
	listTagCategories,
	tagAsset,
	untagAsset,
}));

vi.mock("sonner", () => ({
	toast: { error: vi.fn(), success: vi.fn() },
}));

vi.mock("antd", async () => {
	const React = await import("react");
	const flatten = (options: any[] = []): any[] =>
		options.flatMap((option) => (Array.isArray(option.options) ? flatten(option.options) : [option]));
	return {
		Alert: ({ message, description }: any) => React.createElement("div", { role: "alert" }, message, description),
		Card: ({ children, title }: any) =>
			React.createElement("section", null, React.createElement("h3", null, title), children),
		Empty: ({ description }: any) => React.createElement("div", null, description),
		Select: ({ options, value, onChange, "aria-label": ariaLabel, disabled }: any) =>
			React.createElement(
				"select",
				{
					value: value || "",
					disabled,
					"aria-label": ariaLabel,
					onChange: (event: any) => onChange(event.currentTarget.value),
				},
				[
					React.createElement("option", { key: "", value: "" }, "请选择"),
					...flatten(options).map((option) =>
						React.createElement("option", { key: option.value, value: option.value }, option.label),
					),
				],
			),
		Space: ({ children }: any) => React.createElement("div", null, children),
		Spin: ({ children }: any) => React.createElement("div", null, children),
		Tooltip: ({ children }: any) => React.createElement(React.Fragment, null, children),
	};
});

type Deferred<T> = {
	promise: Promise<T>;
	resolve: (value: T) => void;
	reject: (reason?: unknown) => void;
};

function deferred<T>(): Deferred<T> {
	let resolve!: (value: T) => void;
	let reject!: (reason?: unknown) => void;
	const promise = new Promise<T>((resolvePromise, rejectPromise) => {
		resolve = resolvePromise;
		reject = rejectPromise;
	});
	return { promise, resolve, reject };
}

async function flush() {
	await act(async () => {
		await Promise.resolve();
		await Promise.resolve();
		await Promise.resolve();
	});
}

async function renderAndFlush(ui: ReactElement): Promise<{ container: HTMLElement; root: Root; unmount: () => void }> {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	await act(async () => {
		root = createRoot(container);
		root.render(ui);
	});
	await flush();
	return {
		container,
		root,
		unmount: () => {
			act(() => root.unmount());
			container.remove();
		},
	};
}

describe("GovernedAssetTagPanel", () => {
	beforeEach(() => {
		(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
		listAssetTags.mockResolvedValue([]);
		listTagCategories.mockResolvedValue([]);
		listAllEnabledCatalogTags.mockResolvedValue([]);
		tagAsset.mockResolvedValue({ created: 1, skipped: 0, removed: 0 });
		untagAsset.mockResolvedValue({ created: 0, skipped: 0, removed: 1 });
	});

	afterEach(() => {
		(globalThis as any).IS_REACT_ACT_ENVIRONMENT = false;
		vi.clearAllMocks();
		document.body.innerHTML = "";
	});

	it("stays read-only until the backend explicitly grants tag permission", async () => {
		const pending = deferred<{ canTag: boolean }>();
		getAssetTagCapability.mockReturnValueOnce(pending.promise);
		const { GovernedAssetTagPanel } = await import("./GovernedAssetTagPanel");
		const { container, unmount } = await renderAndFlush(
			<GovernedAssetTagPanel
				assetType="CATALOG_DOMAIN"
				assetKey="tenant:default/env:prod/dialect:generic/catalog_domain:pjm-qa"
			/>,
		);
		const select = container.querySelector("select[aria-label='添加业务数据标签']") as HTMLSelectElement;

		expect(select.disabled).toBe(true);
		await act(async () => pending.resolve({ canTag: true }));
		await flush();
		expect(select.disabled).toBe(false);
		unmount();
	});

	it("remains read-only when the capability request fails", async () => {
		getAssetTagCapability.mockRejectedValueOnce(new Error("permission service unavailable"));
		const { GovernedAssetTagPanel } = await import("./GovernedAssetTagPanel");
		const { container, unmount } = await renderAndFlush(
			<GovernedAssetTagPanel
				assetType="CATALOG_DOMAIN"
				assetKey="tenant:default/env:prod/dialect:generic/catalog_domain:pjm-qa"
			/>,
		);

		const select = container.querySelector("select[aria-label='添加业务数据标签']") as HTMLSelectElement;
		expect(select.disabled).toBe(true);
		expect(container.textContent).toContain("您可以查看业务数据标签，但不能修改当前资产");
		unmount();
	});

	it("does not apply a stale grant after the asset identity changes", async () => {
		const stale = deferred<{ canTag: boolean }>();
		getAssetTagCapability.mockReturnValueOnce(stale.promise).mockResolvedValueOnce({ canTag: false });
		const { GovernedAssetTagPanel } = await import("./GovernedAssetTagPanel");
		const firstKey = "tenant:default/env:prod/dialect:generic/catalog_domain:first";
		const secondKey = "tenant:default/env:prod/dialect:generic/catalog_domain:second";
		const { container, root, unmount } = await renderAndFlush(
			<GovernedAssetTagPanel assetType="CATALOG_DOMAIN" assetKey={firstKey} />,
		);

		await act(async () => {
			root.render(<GovernedAssetTagPanel assetType="CATALOG_DOMAIN" assetKey={secondKey} />);
		});
		await flush();
		await act(async () => stale.resolve({ canTag: true }));
		await flush();

		const select = container.querySelector("select[aria-label='添加业务数据标签']") as HTMLSelectElement;
		expect(select.disabled).toBe(true);
		expect(getAssetTagCapability).toHaveBeenLastCalledWith({
			assetType: "CATALOG_DOMAIN",
			assetKey: secondKey,
		});
		unmount();
	});
});
