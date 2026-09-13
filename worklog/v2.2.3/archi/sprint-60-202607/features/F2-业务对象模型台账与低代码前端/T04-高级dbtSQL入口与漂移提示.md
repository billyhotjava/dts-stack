# T04: 高级 dbt SQL 入口与漂移提示

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

为高级开发保留直接 dbt SQL 能力，并在回到 DTS 时展示来源、解析和漂移结果。

## 技术设计

- 模型详情增加“在 dbt 中维护”入口，跳转项目文件浏览或 SQL IDE。
- dbt 原生模型显示 SQL 文件路径、manifest 时间、字段快照和 drift 状态。
- drift 不在前端自行判断，统一消费 `/api/modeling/vnext/model-specs/{id}/drift`。

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/ModelTemplatesPage.tsx`
- `source/dts-platform-webapp/src/pages/modeling/components/ModelFileBrowser.tsx`
- 高级入口 source-contract 与 Playwright 测试。

## 验证

- [ ] 文件不存在、解析失败和漂移均有明确提示。
- [ ] 普通用户不被强制跳入 SQL 编辑器。

## 完成标准

- [ ] 高级开发路径和低代码路径都能回到同一模型台账。
