// @vitest-environment jsdom

import type { ReactElement, ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const translations = vi.hoisted<Record<string, string>>(() => ({
	"sys.nav.group.integration": "数据集成",
	"sys.nav.portal.integration": "数据集成",
	"sys.nav.group.resources": "资源目录",
	"sys.nav.portal.sources": "数据源",
	"sys.nav.portal.files": "文件接入",
}));

vi.mock("@/locales/use-locale", () => ({
	default: () => ({
		t: (key: string) => translations[key] || key,
	}),
}));

vi.mock("@/components/icon", () => ({ Icon: () => null }));

vi.mock("@/ui/collapsible", () => ({
	Collapsible: ({ children }: { children: ReactNode }) => <div>{children}</div>,
	CollapsibleTrigger: ({ children }: { children: ReactNode }) => <>{children}</>,
	CollapsibleContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("./nav-list", () => ({
	NavList: ({ data }: { data: { title: string } }) => (
		<li>
			<span data-testid="nav-root-title">{translations[data.title] || data.title}</span>
		</li>
	),
}));

import { NavGroup } from "./nav-group";

let container: HTMLDivElement;
let root: Root;

const render = (element: ReactElement) => {
	act(() => root.render(element));
};

const textCount = (text: string) =>
	Array.from(container.querySelectorAll("span")).filter((element) => element.textContent === text).length;

beforeEach(() => {
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(() => {
	act(() => root.unmount());
	container.remove();
});

describe("vertical NavGroup", () => {
	it("单根菜单翻译后与分组同名时只显示根菜单名称", () => {
		render(
			<NavGroup
				name="sys.nav.group.integration"
				items={[{ path: "/integration", title: "sys.nav.portal.integration" }]}
			/>,
		);

		expect(textCount("数据集成")).toBe(1);
		expect(container.querySelectorAll('[data-testid="nav-root-title"]')).toHaveLength(1);
	});

	it("单根菜单与分组翻译后不同名时保留分组名称", () => {
		render(
			<NavGroup
				name="sys.nav.group.resources"
				items={[{ path: "/integration/sources", title: "sys.nav.portal.sources" }]}
			/>,
		);

		expect(textCount("资源目录")).toBe(1);
		expect(textCount("数据源")).toBe(1);
	});

	it("多根菜单即使首项与分组翻译后同名也保留分组名称", () => {
		render(
			<NavGroup
				name="sys.nav.group.integration"
				items={[
					{ path: "/integration", title: "sys.nav.portal.integration" },
					{ path: "/integration/files", title: "sys.nav.portal.files" },
				]}
			/>,
		);

		expect(textCount("数据集成")).toBe(2);
		expect(textCount("文件接入")).toBe(1);
	});
});
