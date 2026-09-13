# T02: 四页统一 URL 状态协议

**优先级**: P1
**状态**: READY
**依赖**: T01（筛选状态已收敛到 hook 入参，才好统一序列化）

## 目标

影响分析、字段血缘、快照对比与图谱页使用同一套 URL 状态协议，做到刷新恢复、链接可分享、跨页深链一致——现状是只有图谱页有（账本#16）。

## 技术设计 (Contract-first)

### 协议定义（沿用图谱页现有参数名，账本#16）

| 参数 | 类型 | 默认（省略时） | 适用页 |
|---|---|---|---|
| `datasetId` | uuid | 无 | 全部 |
| `direction` | `UPSTREAM`\|`DOWNSTREAM` | `BOTH` 省略 | 全部 |
| `depth` | 1-10 | `3` 省略 | 全部 |
| `project` | string | 空省略 | 全部 |
| `layers` | 逗号分隔 | 空省略 | impact / graph / columns |
| `changed` | int hours | `0` 省略 | impact / graph / columns |
| `at` | ISO Instant | 空省略 | impact / graph / columns |
| `layout` | `TB` | `LR` 省略 | graph |
| `columns` | `1` | 关闭省略 | graph |
| `verification` | `DECLARED`\|`KNOWN_UNVERIFIED`\|`VERIFIED` | 空省略 | impact（**新增**，承接 F3/T02 的「去核验」跳转） |
| `from` / `to` | ISO Instant | 空省略 | diff（**新增**，使对比结果可分享） |

### 实现契约

新建 `features/catalog/lineageUrlState.ts`：

```ts
export function readLineageUrlState(params: URLSearchParams, page: LineageSection): LineageUrlState;
export function writeLineageUrlState(params: URLSearchParams, state: LineageUrlState, page: LineageSection): URLSearchParams;
```

- 写入一律 `setSearchParams(next, { replace: true })`，且**只有 `next.toString() !== current.toString()` 时才写**（照抄图谱页 `LineageGraphPage.tsx:138-139` 的既有防抖写法，避免回写风暴）。
- 默认值省略规则与图谱页现状**逐条一致**，保证既有链接继续有效。
- 关键词（`keyword`）**不进 URL**——它是纯前端过滤且每键变化，进 URL 会污染历史记录（与 T01 的 queryKey 决策同源）。

### `verification` 参数语义

在 impact 页，`verification` 作用于**边的前端过滤**（不改后端 `impact` 契约）。F3/T02 中临时加在 `LineageImpactPage` 的最小消费实现**必须在本 Task 中删除**，改走统一协议——避免留下两套解析（F3/T02 已声明此约束）。

### 零回归红线

`components/lineage/lineageF2.source-contract.test.ts:22-33`（账本#16）逐条断言了图谱页的 URL 读写。本 Task 重构后这些断言若因写法变化而失效，**不得修改测试来迁就实现**；应改为等价断言并在 PR 说明中逐条对照。若断言语义确需调整，须在 Sprint README 追加 ADR。

## UI 交互规格

无新增控件。用户可感知：

- 在任一页设好筛选 → F5 刷新 → 筛选保持。
- 复制地址栏发给同事 → 同事看到同一视图。
- 快照对比设好起止时间 → 链接可分享（此前完全丢失）。

## 影响范围

| 文件 | 改动 |
|---|---|
| `features/catalog/lineageUrlState.ts` | **新建** |
| `pages/catalog/LineageGraphPage.tsx` | 现有 URL 逻辑（`:37-152`）改走统一模块，行为不变 |
| `pages/catalog/LineageImpactPage.tsx` | 新增 URL 读写 + `verification` 过滤（并移除 F3/T02 的临时实现） |
| `pages/catalog/LineageColumnsPage.tsx` | 新增 URL 读写 |
| `pages/catalog/LineageDiffPage.tsx` | 新增 URL 读写（含 `from`/`to`） |
| `features/catalog/lineageUrlState.test.ts` | **新建** |
| `pages/catalog/lineageUrlProtocol.source-contract.test.ts` | **新建** |

## 验证 (RED→GREEN)

- [ ] `readWrite_roundTrip`：任意状态 write → read 还原一致（属性式覆盖全部参数组合）
- [ ] `omits_defaults`：默认值不出现在 URL（逐条对照上表）
- [ ] `graph_page_protocol_unchanged`：图谱页产出的 URL 字符串与重构前逐字节一致（用固定状态快照断言）
- [ ] `no_keyword_in_url`：断言 `keyword` 不进 URL
- [ ] `all_four_pages_use_shared_module`：源码契约断言四个页面均 import `lineageUrlState`，且无自写 `params.set(` 残留
- [ ] `impact_consumes_verification_param`：`?verification=KNOWN_UNVERIFIED` 时边被过滤
- [ ] `no_temp_verification_parser`：断言 F3/T02 的临时实现已删除
- [ ] 既有 `lineageF2.source-contract.test.ts` 全绿

### UI 走查

1. 影响分析设 direction=UPSTREAM、depth=5、layers=DWD → 地址栏出现三个参数
2. F5 刷新 → 筛选与结果保持
3. 复制链接到新标签页 → 同一视图
4. 快照对比设起止时间并对比 → 刷新后条件保持
5. 从采集运营台点「去核验 →」→ 影响分析只显示 KNOWN_UNVERIFIED 的边

## Definition of Done

- [ ] 架构：8 条契约测试绿；图谱页 URL 输出逐字节一致
- [ ] UI：走查 5 步全通过（IT-06）
- [ ] 切片：跨页深链（运营台 → 影响分析）在运行实例上生效
- [ ] 无两套 URL 解析残留
- [ ] 无占位证据
