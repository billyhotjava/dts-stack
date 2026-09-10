// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
const calls = vi.hoisted(() => ({ validate: vi.fn(), preview: vi.fn() }));
vi.mock("./qualityExecutionContract", async (original) => ({ ...(await original<object>()), validateQualityDraft: calls.validate, previewQualityDraft: calls.preview }));
import { QualitySqlPreflight } from "./QualitySqlPreflight";
let container: HTMLDivElement; let root: Root;
beforeEach(() => {
 (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
 container=document.createElement("div"); document.body.append(container); root=createRoot(container);
 calls.validate.mockReset(); calls.preview.mockReset();
});
afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
const render = async (sql: string) => act(async () => root.render(<QualitySqlPreflight datasetId="asset-1" sql={sql} />));
const clickPreview = async () => act(async () => { [...container.querySelectorAll("button")].find(button => button.textContent?.includes("试跑当前"))!.click(); });
it("previews the unsaved SQL and never requires a rule id", async () => {
 calls.validate.mockResolvedValue({valid:true,checksum:"current",diagnostics:[],allowedFunctions:"count"});
 calls.preview.mockResolvedValue({checksum:"current",outcome:{qualityOutcome:"PASSED",executionOutcome:"OK",diagnostics:[]}});
 await render("select project_code from public.projects"); await clickPreview();
 expect(calls.preview).toHaveBeenCalledWith("asset-1","select project_code from public.projects");
 expect(container.textContent).toContain("检查通过");
});
it("discards a result when SQL changes during validation", async () => {
 let resolve!: (value: unknown) => void;
 calls.validate.mockReturnValue(new Promise(done => { resolve=done; }));
 await render("select old from public.projects"); await clickPreview();
 await render("select new from public.projects");
 await act(async () => resolve({valid:true,checksum:"old",diagnostics:[],allowedFunctions:"count"}));
 expect(calls.preview).not.toHaveBeenCalled(); expect(container.textContent).not.toContain("静态校验通过");
});
it("shows the rejected function and does not execute it", async () => {
 calls.validate.mockResolvedValue({valid:false,checksum:"bad",diagnostics:[{reasonCode:"UNSUPPORTED_FUNCTION",detail:"pg_sleep"}],allowedFunctions:"count"});
 await render("select pg_sleep(1) from public.projects"); await clickPreview();
 expect(container.textContent).toContain("pg_sleep"); expect(calls.preview).not.toHaveBeenCalled();
});
