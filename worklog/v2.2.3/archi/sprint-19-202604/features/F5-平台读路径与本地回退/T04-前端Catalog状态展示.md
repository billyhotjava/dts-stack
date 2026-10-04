# T04: 前端 Catalog 状态展示

**优先级**: P1
**状态**: DONE
**依赖**: T02, T03

## 目标

在 catalog 相关页面展示 OpenMetadata 命中、未命中、回退和错误状态。

## 范围

- 数据集详情页。
- 血缘页或血缘组件。
- 质量测试列表或质量概览。
- 状态提示、空状态和错误提示。

## 完成标准

- [x] 用户能看出当前是否使用 OpenMetadata 数据。
- [x] 空状态区分“无数据”和“采集失败”。
- [x] 提示信息不暴露敏感配置。
- [x] 前端构建通过。

## 实现记录

- `MetadataPage` 在元数据结果预览中展示数据来源标签和回退原因。
- `MetadataPage` 在表详情中展示详情来源、FQN 与缺失/回退提示。
- `QualityPage` 展示治理运行结果、OpenMetadata、disabled 等来源，并显示 `fallbackReason`。
- 前端构建命令 `pnpm build` 通过。
