# T03: Repository 层 + 替换 MetricGraphDraftService 内存存储

**优先级**: P0
**状态**: DONE（gitnexus_impact=LOW；MetricGraphResourceTest 12/12 绿）
**依赖**: T02

## 目标

引入 Repository 层，把 `MetricGraphDraftService` 的 `ConcurrentHashMap drafts` 换成数据库存储，行为保持不变。

## 技术设计

- 新增 `GraphDraftRepository extends JpaRepository`。
- 重写 `MetricGraphDraftService`：createDraft/getDraft/updateDraft/preflightStoredDraft/graph 全部走 repository；preflightDraft（无状态预检）逻辑不变。
- 保持现有方法签名与返回结构（对 `MetricModelLifecycleService` 与 Resource 透明）。
- **编辑前对 `MetricGraphDraftService` 跑 `gitnexus_impact`，报告 blast radius。**

## 影响范围

- `MetricGraphDraftService`（重写存储后端）。
- 新增 `domain/repository/GraphDraftRepository`。
- 不改 `MetricGraphResource` 对外行为。

## 验证
- [ ] 既有 `MetricGraphResourceTest` 全绿（行为不变）。
- [ ] draft 创建后重启服务仍可读取。

## 完成标准
- [ ] `MetricGraphDraftService` 不再持有内存 Map。
