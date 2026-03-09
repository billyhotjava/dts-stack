# FE-010

## 标题

完成三端构建验证与视觉审计收口。

## 范围

- 三个 webapp 的构建验证
- sprint 状态板与任务卡收口
- 人工视觉核对清单

## 目标

- 确认三端全部可构建
- 确认客户可见路径不再出现假内容
- 确认三端看起来属于同一个产品

## 交付

- 最终构建结果
- 手工审计记录
- 状态板更新

## 验收

- `pnpm -C source/dts-platform-webapp build`
- `pnpm -C source/dts-admin-webapp build`
- `pnpm -C source/dts-analytics-webapp/modern build`
- sprint 文档补齐验证结果与残余风险

## 风险

- 仅靠 build 不能替代视觉走查，必须保留人工页面核对
