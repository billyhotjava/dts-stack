# T01: `Result<T>` 信封 + mock apiClient + 可调延迟模拟

**优先级**: P0
**状态**: READY
**依赖**: F1-T01

## 目标

建立 mock 层的核心运行时：复刻现网 `Result<T>` 信封，提供一个返回 `Promise<Result<T>>` 且可调延迟的 mock apiClient。

## 技术设计

- **Result 信封**（复刻现网 `src/types/api.ts` + `src/types/enum.ts`）：
  ```ts
  export enum ResultStatus { SUCCESS = 200, ERROR = -1, TIMEOUT = 401 }
  export interface Result<T = unknown> { status: ResultStatus; message: string; data: T }
  ```
  放在 `src/mock/types.ts` 或对齐现网 `src/types/api.ts`。
- **mock apiClient**：`src/mock/apiClient.ts` 暴露 `mockOk<T>(data, opts?)` / `mockErr<T>(message, status?)`，统一包成 `Result<T>` 并经一个 `withDelay` 包装返回 `Promise`：
  - 可调延迟：默认 `200~400ms`，可经环境变量 `VITE_MOCK_DELAY` 或参数覆盖；支持 `0`（测试用）。
  - 错误注入钩子（可选）：便于演示加载/失败态。
- **不可变**：fixtures 读出后返回副本（避免调用方改动内存源数据）。
- **分页响应**：若需要分页，复刻现网 `PageResult<T>`（`src/api/ingestion.ts`）形状，注意 `pageNum` 基准与现网一致。

## 影响范围

- 新增 `src/mock/types.ts`（或对齐 `src/types/api.ts`）、`src/mock/apiClient.ts`。
- 被 F3-T02 的 `*Service.ts` 与 fixtures 消费。

## 验证

- [ ] `mockOk`/`mockErr` 返回的对象形状与现网 `Result<T>` 一致。
- [ ] `withDelay` 延迟可调，`VITE_MOCK_DELAY=0` 时近即时返回。
- [ ] 返回数据是副本，外部修改不污染内存源。

## 完成标准

- [ ] `Result<T>` 信封与 `ResultStatus` 复刻现网。
- [ ] mock apiClient 返回 `Promise<Result<T>>` 且延迟可调。
- [ ] 为 service 层提供稳定的 ok/err 构造原语。
