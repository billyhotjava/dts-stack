# T04: artifact diff 与版本

**优先级**: P1
**状态**: READY
**依赖**: T03

## 目标

展示候选 artifact 与当前发布版本的差异，并支持版本回滚。

## 技术设计

- 保存每次 artifact snapshot。
- 展示 SQL/schema.yml/metric doc 差异。
- 区分兼容变更、破坏性变更和需要审批的变更。

## 影响范围

- `source/dts-metrics` artifact snapshot
- `source/dts-metrics-webapp` diff panel

## 验证

- [ ] 删除字段被标记为破坏性变更。
- [ ] 回滚不会删除底层资产。

## 完成标准

- [ ] 发布前用户能看到 artifact diff。
