# F4：存量纠错、治理策略与兼容

**优先级**：P0  
**状态**：DONE

## 目标

让误建草稿可安全改型，让治理要求只在正确阶段生效，并保证旧 revision 与 Sprint-73 已提交的 `implementationPolicy` 数据可读、可迁移、可回滚。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| Preview | `POST .../{id}/reclassify-preview` | targetType、dimensionDefinitionRef；零写 |
| Apply | `POST .../{id}/reclassify` | If-Match、acceptedClearFields、idempotencyKey |
| Policy | WarehousePlanPolicy 扩展 | standardCoverage、qualityGate |
| Classification | 复用 Sprint-72 gate | inherited/propagated/explicit evidence |
| Compatibility | snapshot/implementation adapter | 旧字段可读，新写单一 owner |

## UI/UX 规格

- 模型标题区提供“调整模型类型”，仅符合 eligibility 时可用；
- preview 显示保留、需补、将清理内容和目标层；
- 治理卡区分“逻辑设计可选”“发布策略要求”；
- 历史兼容问题提供迁移入口，不伪装为空。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 实现DRAFT模型改型预检与追加revision | P0 | DONE | F1、F2 |
| T02 | 将治理必填收敛到计划发布策略 | P0 | DONE | F2、Sprint-72/69 |
| T03 | 迁移已提交implementationPolicy并兼容旧输入 | P0 | DONE | F3 |

## Definition of Ready

- [x] eligibility 和 preview/apply DTO 已钉死
- [x] 治理阶段边界已钉死
- [x] compatibility owner 已钉死
- [x] GitNexus 更新到当前 HEAD，Sprint-73 owning symbols 影响审计完成

## 完成标准

- [x] 隔离财务 FACT 样本可预检后生成新 revision，用户模型不被自动修改
- [x] 有实现/发布证据的模型不可原地改型
- [x] 标准/密级/质量不阻断 DESIGNED
- [x] IT-09～IT-11 通过
