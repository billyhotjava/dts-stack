# T02: ModelSpec 模型台账与双模式标识

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: F1-T01

## 目标

让模型管理页面同时展示设计器生成模型和 dbt 原生模型，并明确实现所有权。

## 技术设计

- 设计器模式显示 ModelSpec revision、生成物 checksum 和编译状态。
- dbt 原生模式显示 dbt unique id、文件路径、manifest revision 和漂移状态。
- 模型详情提供“预览 SQL”“查看 schema/tests”“打开 dbt 文件”“查看运行证据”。
- DWD/DWS/ADS 分层筛选继续保留。

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/SemanticModelsPage.tsx`
- `source/dts-platform-webapp/src/pages/modeling/SemanticWorkspaceFrame.tsx`
- 模型台账 source-contract 与 Playwright locator。

## 验证

- [x] 设计器、dbt 原生和兼容只读模型使用不同实现模式文案。
- [x] 待审核/阻断状态有下一步动作字段。
- [x] 旧模型显示兼容只读来源，不被误判为新模型。
- [ ] 真实编译/漂移 API 数据源接入台账。

## 完成标准

- [x] 普通用户和高级开发都能从同一模型台账找到下一步。
