// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { getModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import { useModelDeliveryStatus } from "./useModelDeliveryStatus";

vi.mock("@/api/modelDeliveryStatusApi", () => ({ getModelDeliveryStatus: vi.fn() }));
vi.mock("./services/planningProjectionService", () => ({
	normalizeModelingRequestFailure: (e: any) => ({ message: e.message }),
}));
let root: Root;
let container: HTMLDivElement;
const deferred = () => {
	let resolve!: (value: any) => void;
	const promise = new Promise<any>((r) => {
		resolve = r;
	});
	return { promise, resolve };
};
const model = (id: string, revision = 1) =>
	({ id, revision, checksum: `checksum-${revision}`, compatibilityMode: "CANONICAL" }) as any;
const status = (id: string, revision = 1) =>
	({
		modelSpecId: id,
		modelRevision: revision,
		modelChecksum: `checksum-${revision}`,
		steps: [],
		recommendedStep: "verification",
	}) as any;
function Probe({ id, revision = 1 }: { id: string; revision?: number }) {
	const result = useModelDeliveryStatus(model(id, revision), "test", "", 0);
	return <div>{result.data?.modelSpecId || result.failure || "loading"}</div>;
}
beforeEach(() => {
	vi.clearAllMocks();
	(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.append(container);
	root = createRoot(container);
});
afterEach(() => {
	act(() => root.unmount());
	container.remove();
});
it("late response from another model never replaces current evidence", async () => {
	const a = deferred(),
		b = deferred();
	vi.mocked(getModelDeliveryStatus).mockReturnValueOnce(a.promise).mockReturnValueOnce(b.promise);
	await act(async () => root.render(<Probe id="a" />));
	await act(async () => root.render(<Probe id="b" />));
	await act(async () => b.resolve(status("b")));
	expect(container.textContent).toBe("b");
	await act(async () => a.resolve(status("a")));
	expect(container.textContent).toBe("b");
});
it("revision change clears old results and rejects stale evidence", async () => {
	vi.mocked(getModelDeliveryStatus).mockResolvedValueOnce(status("a"));
	await act(async () => root.render(<Probe id="a" />));
	const late = deferred();
	vi.mocked(getModelDeliveryStatus).mockReturnValueOnce(late.promise);
	await act(async () => root.render(<Probe id="a" revision={2} />));
	expect(container.textContent).toBe("loading");
	await act(async () => late.resolve(status("a", 1)));
	expect(container.textContent).toContain("模型版本已变化");
});
it("failed reads never present a successful state", async () => {
	vi.mocked(getModelDeliveryStatus).mockRejectedValueOnce(new Error("读取失败"));
	await act(async () => root.render(<Probe id="a" />));
	expect(container.textContent).toBe("读取失败");
});
