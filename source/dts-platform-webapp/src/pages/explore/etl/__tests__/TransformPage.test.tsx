// @vitest-environment jsdom

import { act, type ReactElement, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterAll, afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const reactActEnvironment = globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean };
const previousReactActEnvironment = reactActEnvironment.IS_REACT_ACT_ENVIRONMENT;
reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;

const { getExecutionsObservability, getGovernanceOverview, getLatestExecution, getTasks, routerPush } = vi.hoisted(
	() => ({
		getExecutionsObservability: vi.fn(),
		getGovernanceOverview: vi.fn(),
		getLatestExecution: vi.fn(),
		getTasks: vi.fn(),
		routerPush: vi.fn(),
	}),
);

vi.mock("@/api/ingestion", () => ({
	ingestionTaskAPI: {
		getExecutionsObservability,
		getGovernanceOverview,
		getLatestExecution,
		getTasks,
	},
}));

vi.mock("@/routes/hooks", () => ({
	useRouter: () => ({ push: routerPush }),
}));

vi.mock("@/components/page-header", () => ({
	PageHeader: ({ actions, title }: { actions?: ReactNode; title?: ReactNode }) => (
		<header>
			<h1>{title}</h1>
			{actions}
		</header>
	),
}));

vi.mock("@/components/table", () => ({
	CompactTable: ({ pagination }: { pagination?: { onChange?: (page: number, pageSize: number) => void } }) => (
		<button type="button" data-testid="go-to-page-2" onClick={() => pagination?.onChange?.(2, 10)}>
			第 2 页
		</button>
	),
}));

vi.mock("antd", async () => {
	const React = await import("react");
	const Wrapper = ({ children }: { children?: ReactNode }) => React.createElement("div", null, children);
	return {
		Alert: Wrapper,
		Button: ({ children, onClick, ...props }: any) =>
			React.createElement(
				"button",
				{
					type: "button",
					onClick,
					"data-testid": props["data-testid"],
				},
				children,
			),
		Card: ({ children, extra, title }: any) =>
			React.createElement("section", null, React.createElement("header", null, title, extra), children),
		message: {
			error: vi.fn(),
			success: vi.fn(),
			warning: vi.fn(),
		},
		Modal: ({ children, open }: any) => (open ? React.createElement("div", null, children) : null),
		Progress: Wrapper,
		Select: ({ onChange, options = [], value, ...props }: any) =>
			React.createElement(
				"select",
				{
					"aria-label": props["aria-label"],
					"data-testid": props["data-testid"],
					value,
					onChange: (event: any) => onChange(event.currentTarget.value),
				},
				options.map((option: any) =>
					React.createElement("option", { key: option.value, value: option.value }, option.label),
				),
			),
		Space: Wrapper,
		Tag: Wrapper,
		Typography: { Text: Wrapper },
	};
});

import TransformPage from "../TransformPage";

let root: Root | null = null;
let host: HTMLDivElement | null = null;

async function flush() {
	await act(async () => {
		await Promise.resolve();
		await Promise.resolve();
		await Promise.resolve();
	});
}

async function renderAndFlush(ui: ReactElement) {
	host = document.createElement("div");
	document.body.appendChild(host);
	await act(async () => {
		root = createRoot(host as HTMLDivElement);
		root.render(ui);
	});
	await flush();
	return host;
}

beforeEach(() => {
	getTasks.mockResolvedValue({ content: [], totalElements: 25 });
	getExecutionsObservability.mockResolvedValue({});
	getGovernanceOverview.mockResolvedValue({});
	getLatestExecution.mockResolvedValue(null);
});

afterEach(() => {
	if (root && host) {
		act(() => root?.unmount());
		host.remove();
	}
	root = null;
	host = null;
	vi.clearAllMocks();
});

afterAll(() => {
	reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = previousReactActEnvironment;
});

describe("TransformPage task filters", () => {
	it("returns to the first page and requests the selected status", async () => {
		const container = await renderAndFlush(<TransformPage />);
		expect(getTasks).toHaveBeenCalledWith({ page: 0, size: 10 });

		await act(async () => {
			(container.querySelector('[data-testid="go-to-page-2"]') as HTMLButtonElement).click();
		});
		await flush();
		expect(getTasks).toHaveBeenCalledWith({ page: 1, size: 10 });

		const statusFilter = container.querySelector(
			'[data-testid="platform-transform-status-filter"]',
		) as HTMLSelectElement;
		await act(async () => {
			statusFilter.value = "active";
			statusFilter.dispatchEvent(new Event("change", { bubbles: true }));
		});
		await flush();

		expect(getTasks).toHaveBeenLastCalledWith({ page: 0, size: 10, status: "active" });
	});
});
