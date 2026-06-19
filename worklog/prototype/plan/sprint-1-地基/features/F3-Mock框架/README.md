# F3: Mock 框架

**优先级**: P0
**状态**: READY

## 目标

复刻现网 API 契约的 mock 层：分域 `*Service.ts` 返回 `Promise<Result<T>>`，背后内存 fixtures + 可调延迟，`VITE_USE_MOCK` 开关一键切真实 axios；并提供"销售准备项目"贯穿 4 阶段的种子数据骨架与"重置样例数据"开发入口，保证黄金主线端到端可点可走。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | `Result<T>` 信封 + mock apiClient + 可调延迟 | P0 | READY | F1-T01 |
| T02 | fixtures 框架 + `VITE_USE_MOCK` 开关 + 切真 axios 适配层 | P0 | READY | T01 |
| T03 | "销售准备项目"种子数据骨架（贯穿 4 阶段） | P0 | READY | T02 |
| T04 | "重置样例数据"开发入口 | P1 | READY | T03 |

## 完成标准

- [ ] `Result<T> = { status: ResultStatus; message: string; data: T }` 复刻现网 `src/types/api.ts`；mock apiClient 返回 `Promise<Result<T>>` 且支持可调延迟。
- [ ] `VITE_USE_MOCK` 开关：开=走内存 fixtures，关=走真实 axios 适配层（不接真后端但代码路径保留）。
- [ ] 至少建立项目域 + 4 阶段域的 `*Service.ts` 骨架，命名对齐现网 `src/api/services`。
- [ ] "销售准备项目"种子数据贯穿 PLM 订单+ERP 客户 → 去重/连接 → ODS 宽表 → 销售达成率指标，可被 service 读取并用于派生阶段状态。
- [ ] "重置样例数据"开发入口可把内存 fixtures 复位到初始种子。
