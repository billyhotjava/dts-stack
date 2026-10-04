// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { isCandidateInFlight, ModelBuildProgressNotice } from "./ModelBuildProgressNotice";

let container: HTMLDivElement;
let root: Root;

const button = (label: string) =>
	Array.from(container.querySelectorAll("button")).find((item) => item.textContent?.trim() === label);

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

describe("build progress notice", () => {
	it("treats only server-advanced statuses as in flight", () => {
		expect(isCandidateInFlight("BUILDING")).toBe(true);
		expect(isCandidateInFlight("QUALITY_RUNNING")).toBe(true);
		expect(isCandidateInFlight("PUBLISHING")).toBe(true);
		expect(isCandidateInFlight("BUILD_FAILED")).toBe(false);
		expect(isCandidateInFlight("BUILT")).toBe(false);
		expect(isCandidateInFlight(null)).toBe(false);
		expect(isCandidateInFlight(undefined)).toBe(false);
	});

	it("drops a pending confirmation when the server withdraws the exit", async () => {
		const onAbandon = vi.fn(async () => true);
		await act(async () => root.render(<ModelBuildProgressNotice busy={false} canAbandon onAbandon={onAbandon} />));
		await act(async () => button("放弃本次构建")?.click());
		expect(button("确认放弃本次构建")).toBeDefined();

		await act(async () =>
			root.render(<ModelBuildProgressNotice busy={false} canAbandon={false} onAbandon={onAbandon} />),
		);
		await act(async () => root.render(<ModelBuildProgressNotice busy={false} canAbandon onAbandon={onAbandon} />));

		expect(button("确认放弃本次构建")).toBeUndefined();
		expect(button("放弃本次构建")).toBeDefined();
		expect(onAbandon).not.toHaveBeenCalled();
	});

	it("keeps the confirmation open when abandonment fails so the user can retry or back out", async () => {
		const onAbandon = vi.fn(async () => false);
		await act(async () => root.render(<ModelBuildProgressNotice busy={false} canAbandon onAbandon={onAbandon} />));
		await act(async () => button("放弃本次构建")?.click());
		await act(async () => button("确认放弃本次构建")?.click());

		expect(onAbandon).toHaveBeenCalledTimes(1);
		expect(button("确认放弃本次构建")).toBeDefined();
		await act(async () => button("暂不放弃")?.click());
		expect(button("放弃本次构建")).toBeDefined();
	});

	it("disables every action while another command is running", async () => {
		await act(async () => root.render(<ModelBuildProgressNotice busy canAbandon onAbandon={vi.fn()} />));
		expect(button("放弃本次构建")?.disabled).toBe(true);
	});

	it("names the unconfirmed dispatch code instead of a generic running message", async () => {
		await act(async () =>
			root.render(
				<ModelBuildProgressNotice
					busy={false}
					canAbandon={false}
					onAbandon={vi.fn()}
					unconfirmedCode="MODEL_AIRFLOW_TRIGGER_UNKNOWN"
				/>,
			),
		);
		expect(container.textContent).toContain("MODEL_AIRFLOW_TRIGGER_UNKNOWN");
		expect(container.textContent).not.toContain("构建进行中");
	});
});
