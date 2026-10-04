# T01: 容器重建与 semanticModelingApi 激活

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
把 `SemanticModelingCenterPage` 从跳转壳重建为真实 sectioned 容器（overview/subjects/objects/metrics/models/publish/runs），渲染原生内容而非 `window.location.replace`；激活 `semanticModelingApi`。

## 技术设计
- `SemanticModelingCenterPage({ section })`：去掉 `window.location.replace`，按 section 渲染对应原生子页/Tab；保留 `SemanticModelingSection` 类型。
- 容器布局参照平台既有 modeling 页（antd，Chrome 95 兼容，不引 structuredClone/新 API）。
- 接入 `semanticModelingApi`（listSemanticModels 等）做一个最小可用 section（如 overview workbench-overview）验证贯通。
- **灰度**：feature flag（如 `VITE_SEMANTIC_NATIVE`）控制"原生容器 vs 跳转 dts-metrics"，默认可先开发态原生、生产态跳转，保回退（与 F3-T01 联动）。

## 影响范围
- `dts-platform-webapp`：`SemanticModelingCenterPage.tsx`（+ 可能新增 section 子组件目录）、`semanticModelingApi.ts`（激活）。

## 验证
- [ ] flag 开 → 渲染原生容器（overview 拉到 `/api/semantic/workbench` 数据）；flag 关 → 仍跳转（回退在）。
- [ ] tsc + vite build 通过；Chrome 95 兼容。

## 完成标准
- [ ] 容器去跳转、按 section 渲染、semanticModelingApi 激活、灰度可回退、构建通过。
