# T03: REST 兼容与迁移开关

**优先级**: P1
**状态**: READY
**依赖**: T01, T02

## 目标

确保 `/api/infra/excel-import/*` 现有主响应字段保持兼容，并在必要时为新旧实现并行提供开关，降低灰度风险。

## 完成标准

- [ ] 前端 `dataSourcesService.ts` 不需要大改
- [ ] `csvPath / csvContainerPath / columns / preview / errorCount` 继续返回
- [ ] 如引入 feature flag，需在回滚 SOP 中登记
