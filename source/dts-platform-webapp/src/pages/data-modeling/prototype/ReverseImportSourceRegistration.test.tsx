// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { expect, it, vi } from "vitest";
import { ReverseImportSourceRegistration } from "./ReverseImportSourceRegistration";
vi.mock("./ModelSourceInventoryDialog", () => ({
	ModelSourceInventoryDialog: ({ planId, onSourcesChanged, onClose }: any) => (
		<div>
			<span>{planId}</span>
			<button
				onClick={() =>
					onSourcesChanged([{ bindingId: "ods-budget", confirmationStatus: "CONFIRMED", freshness: "CURRENT" }], planId)
				}
			>
				登记测试来源
			</button>
			<button onClick={onClose}>关闭登记</button>
		</div>
	),
}));
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
it("registers sources for the selected import context without any model and refreshes its parent", async () => {
	const host = document.createElement("div");
	document.body.append(host);
	const root = createRoot(host);
	const saved = vi.fn();
	try {
		await act(async () =>
			root.render(<ReverseImportSourceRegistration planId="department-context" onSourcesChanged={saved} />),
		);
		const click = async (text: string) => {
			const button = [...host.querySelectorAll("button")].find((b) => b.textContent === text)!;
			await act(async () => button.click());
		};
		await click("登记并确认来源");
		expect(host.textContent).toContain("department-context");
		await click("登记测试来源");
		expect(saved).toHaveBeenCalledWith(
			expect.arrayContaining([expect.objectContaining({ bindingId: "ods-budget" })]),
			"department-context",
		);
		await click("关闭登记");
		expect(host.textContent).not.toContain("登记测试来源");
	} finally {
		await act(async () => root.unmount());
		host.remove();
	}
});
