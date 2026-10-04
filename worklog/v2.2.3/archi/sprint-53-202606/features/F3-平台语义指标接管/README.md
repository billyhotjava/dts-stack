# F3: 平台语义指标接管

**优先级**: P0  
**状态**: DONE

## 目标

补齐平台新指标页面接管旧 dts-metrics 的关键缺口，确保退役后用户仍能完成指标建模、发布和黄金链路跳转。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 黄金链路模型阶段路由迁移 | P0 | DONE | F1/T03 |
| T02 | 业务对象表映射编辑闭环 | P0 | DONE | F0/T02 |
| T03 | 发布链路严格失败态 | P0 | DONE | F0/T02 |
| T04 | 指标工作台接管验收增强 | P0 | DONE | T01 T02 T03 |

## 完成标准

- [x] `MODEL_READY` 不再跳旧 `/modeling/semantic-center`。
- [x] 业务对象页面可以编辑并保存 table mappings。
- [x] publish dbt + register BI + register lineage 任一步失败不会提示全成功。
- [x] 指标工作台 source-contract 覆盖接管能力。
