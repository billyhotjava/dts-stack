# T02: fixtures 框架 + `VITE_USE_MOCK` 开关 + 切真 axios 适配层

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

建立内存 fixtures 框架与分域 `*Service.ts` 骨架，通过 `VITE_USE_MOCK` 开关在"内存 mock"与"真实 axios"之间一键切换（真实路径保留但不接后端）。

## 技术设计

- **fixtures 框架**：`src/mock/fixtures/`，按域拆分（`projects.ts` / `connect.ts` / `integrate.ts` / `assets.ts` / `metrics.ts`），内存可变状态集中管理，提供初始种子（F3-T03 填充）。
- **分域 service**：`src/mock/services/*Service.ts`，命名对齐现网 `src/api/services`（如 `dataSourcesService.ts` / `goldenChainService.ts`）。每个方法返回 `Promise<Result<T>>`：
  - `VITE_USE_MOCK` 开 → 读内存 fixtures 经 T01 apiClient 包成 `Result<T>`。
  - `VITE_USE_MOCK` 关 → 走真实 axios 适配层。
- **开关 + 适配层**：
  - `.env` 设 `VITE_USE_MOCK=1`（默认开）。
  - `src/mock/transport.ts`：根据 `import.meta.env.VITE_USE_MOCK` 选择 `mockTransport` 或 `axiosTransport`；`axiosTransport` 复刻现网 `apiClient` 拦截器形状（baseURL、`Result` 解包），但原型不接真后端——路径存在、可编译、未启用。
  - service 仅依赖 `transport` 抽象，切换零改动业务代码（仓储模式思想）。
- **类型**：各域 DTO 类型集中 `src/mock/types/` 或随 service 文件，显式 `interface`。

## 影响范围

- 新增 `src/mock/fixtures/*`、`src/mock/services/*Service.ts`、`src/mock/transport.ts`、`.env`（`VITE_USE_MOCK`）。
- 依赖 F3-T01 apiClient；被 F4 外壳/项目门户消费。

## 验证

- [ ] `VITE_USE_MOCK=1` 时 service 返回 fixtures 数据（`Result.status=SUCCESS`）。
- [ ] `VITE_USE_MOCK=0` 时切到 axiosTransport 路径（不接后端，验证编译/路由切换，不验证响应）。
- [ ] 切换开关无需改动任何 service/调用方代码。
- [ ] service 命名与方法形状对齐现网 `*Service.ts`。

## 完成标准

- [ ] fixtures 框架 + 分域 service 骨架就位。
- [ ] `VITE_USE_MOCK` 一键切换 mock/真实 axios，真实路径保留可编译。
- [ ] service 只依赖 transport 抽象，回植与切真零摩擦。
