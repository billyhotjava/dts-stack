# FE-002

## 标题

在逻辑建模页中移除 rollback 交互，改为 dbt 模型产出表维护弹窗。

## 范围

- `SqlModelingPage.tsx`

## 目标

- “清空产出表 / 重建产出表”直接对应当前模型 relation

## 交付

- 新的产出表维护弹窗
- API 接线与状态提示

## 验收

- 页面不再调用 rollback 代理
- 用户能看到 relation 与 materialized 信息

## 当前进度

- 状态：TODO

## 风险

- 若弹窗信息不足，用户可能仍然误以为这是接入回滚
