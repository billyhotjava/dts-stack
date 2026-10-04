// @vitest-environment jsdom
import { expect, it, vi } from "vitest";
vi.mock("./apiClient", () => ({ default: { post: vi.fn() } }));
import api from "./apiClient";
import { saveModelDraftOperation } from "./modelSpecApi";

it("omits both blank creation contexts in the single atomic request", async () => {
    const command = { create: { planId: "", idempotencyKey: "retry-key" }, modelSpec: { planId: "" }, saveMode: "DEFINITION_ONLY" };
    await saveModelDraftOperation(command as any);
    const sent = vi.mocked(api.post).mock.calls.at(-1)![0] as any;
    expect(sent.url).toContain("/draft-operations");
    expect(JSON.parse(JSON.stringify(sent.data))).toEqual({ create: { idempotencyKey: "retry-key" }, modelSpec: {}, saveMode: "DEFINITION_ONLY" });
    expect(command.create.planId).toBe("");
});

it("preserves explicit contexts for server validation", async () => {
    await saveModelDraftOperation({ create: { planId: "invalid" }, modelSpec: { planId: "different" } } as any);
    const sent = vi.mocked(api.post).mock.calls.at(-1)![0] as any;
    expect(sent.data.create.planId).toBe("invalid");
    expect(sent.data.modelSpec.planId).toBe("different");
});
