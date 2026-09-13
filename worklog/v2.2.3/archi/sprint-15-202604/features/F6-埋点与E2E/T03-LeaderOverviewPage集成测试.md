# T03: LeaderOverviewPage 集成测试

**优先级**: P1
**状态**: READY
**依赖**: F5/T04

## 目标

用 RTL + msw（mock HTTP）写集成测试，覆盖 `LeaderOverviewPage` 主要数据流转、错误态、三角色分支渲染，不依赖真实后端。

## 技术设计

### 测试工具

- **vitest** + `@testing-library/react` + `@testing-library/user-event`
- **msw**（Mock Service Worker）拦截：`/api/workbench/leader-overview`、`/api/catalog/domains`、`/api/depts/tree`（或项目实际路径）、`/api/analytics/screens`

### 测试文件

`src/pages/workbench/LeaderOverviewPage.integration.test.tsx`：

```ts
import { setupServer } from "msw/node";
import { http, HttpResponse } from "msw";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LeaderOverviewPage } from "./LeaderOverviewPage";

const server = setupServer();

function mockRole(role: "EMP" | "DEPT_LEADER" | "INST_LEADER", deptCode = "D001") {
  // mock useWorkbenchRole via vi.mock 或直接 mock userStore
}

describe("LeaderOverviewPage", () => {
  beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
  afterEach(() => server.resetHandlers());
  afterAll(() => server.close());

  test("INST_LEADER sees 4 KPI cards + matrix when data ready", async () => {
    mockRole("INST_LEADER");
    server.use(
      http.get("/api/catalog/domains", () => HttpResponse.json([{ code: "FIN", name: "财务" }])),
      http.get("/api/workbench/leader-overview", () =>
        HttpResponse.json({
          generatedAt: "2026-04-24T08:00:00Z",
          scope: "ALL",
          effectiveDeptCode: null,
          timeRange: "MONTH",
          kpis: { reportsTotal: 248, reportsNewInPeriod: 12, visitsInPeriod: 14321, visitsMoM: 0.08, assetsTotal: 1284, assetsNewInPeriod: 54, assetsS1: 84, assetsS1Ratio: 0.065, assetsS1S2: 180 },
          topReports: [{ id: "r1", title: "月度财务月报", visits: 312, bizDomain: "FIN", classification: "S2", lastVisitedAt: "2026-04-23T09:00:00Z" }],
          topAssets: [{ id: "a1", name: "核心客户表", classification: "S1", updatedAt: "2026-04-22T10:00:00Z", bizDomain: "FIN" }],
          domainMatrix: [{ domain: "FIN", domainName: "财务", visits: 5210 }],
        })
      )
    );
    render(<LeaderOverviewPage />);
    await waitFor(() => expect(screen.getByText("所内报表")).toBeInTheDocument());
    expect(screen.getByText("数据资产")).toBeInTheDocument();
    expect(screen.getByText("核心资产（S1）")).toBeInTheDocument();
    expect(screen.getByText("月度财务月报")).toBeInTheDocument();
    expect(screen.getByText("核心客户表")).toBeInTheDocument();
  });

  test("DEPT_LEADER sees 3 KPI cards and no matrix", async () => { /* ... */ });
  test("EMP sees 3 KPI cards and no matrix", async () => { /* ... */ });

  test("hides bizDomain filter when /catalog/domains fails", async () => {
    mockRole("INST_LEADER");
    server.use(
      http.get("/api/catalog/domains", () => HttpResponse.json({}, { status: 500 })),
      http.get("/api/workbench/leader-overview", () => HttpResponse.json({ /* minimal shape */ }))
    );
    render(<LeaderOverviewPage />);
    await waitFor(() => expect(screen.queryByText("全部业务域")).not.toBeInTheDocument());
  });

  test("shows error alert + retry on leader-overview 500", async () => {
    mockRole("INST_LEADER");
    let calls = 0;
    server.use(
      http.get("/api/workbench/leader-overview", () => {
        calls += 1;
        return calls === 1 ? HttpResponse.json({}, { status: 500 }) : HttpResponse.json({ /* full payload */ });
      })
    );
    render(<LeaderOverviewPage />);
    const retry = await screen.findByRole("button", { name: /重试/ });
    await userEvent.click(retry);
    await waitFor(() => expect(screen.queryByText("暂时拿不到数据")).not.toBeInTheDocument());
  });

  test("changing timeRange refetches with new param", async () => {
    mockRole("INST_LEADER");
    let lastTimeRange: string | null = null;
    server.use(
      http.get("/api/workbench/leader-overview", ({ request }) => {
        lastTimeRange = new URL(request.url).searchParams.get("timeRange");
        return HttpResponse.json({ /* minimal payload */ });
      })
    );
    render(<LeaderOverviewPage />);
    await waitFor(() => expect(lastTimeRange).toBe("MONTH"));
    await userEvent.click(screen.getByText("本月"));
    await userEvent.click(screen.getByText("本季"));
    await waitFor(() => expect(lastTimeRange).toBe("QUARTER"));
  });

  test("clicking domain cell sets bizDomain filter and refetches", async () => { /* ... */ });
  test("non-INST_LEADER requesting scope=ALL is effectively downgraded by server returning scope=DEPT", async () => {
    // mock 后端返回 effectiveDeptCode + scope=DEPT，前端据此工作
  });
});
```

### 注意

- 用 `vi.mock("@/store/userStore")` 提供可控的 `useUserInfo` / `useUserRoles`。
- msw 的 unhandled 请求设为 "error"，防止漏 mock 出现真实 fetch。
- 所有 fetch 的默认 timeout 在测试里降到 100ms，避免慢测试。

## 影响范围

- 新增：`src/pages/workbench/LeaderOverviewPage.integration.test.tsx`
- 可能新增：`src/test-utils/msw.ts`（共享 msw server 配置）

## 验证

- [ ] 所有测试通过。
- [ ] 测试在 CI 上重复跑 20 次无 flaky。
- [ ] 对 `LeaderOverviewPage.tsx` 的行覆盖率贡献 ≥ 70%（单测中 `fetch × filter × error` 三条路径均被覆盖）。

## 完成标准

- [ ] 至少 7 个测试用例（三角色 × 软依赖 × 错误态 × 时间切换 × 色块联动）。
- [ ] 无 real fetch 泄露；msw 日志干净。
- [ ] 测试运行时间 < 10s。
