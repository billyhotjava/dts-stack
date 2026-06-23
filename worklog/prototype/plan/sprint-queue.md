# dts-platform 原型 · Sprint 队列

> 目标：把现网按"职能筒仓"组织的 dts-platform，重构为一条 **项目/工作空间 + 阶段向导**（连接→集成→资产→指标）的统一 ELT 旅程原型。
> 形态：纯前端可复用 React 骨架（与现网同栈），mock API 契约，Chrome 95 兼容。
> 设计依据：[2026-06-19-dts-platform-unified-elt-redesign-design.md](./2026-06-19-dts-platform-unified-elt-redesign-design.md)

## Sprint 总览

| Sprint | 主题 | Feature 数 | 状态 | 依赖 |
|--------|------|-----------|------|------|
| [S1](./sprint-1-地基/README.md) | 地基（脚手架/设计系统/mock/外壳） | 4 | DONE | - |
| [S1.5](./sprint-1.5-工作区层/README.md) | 工作区层（平台/工作区/项目 三层 + 数据源混合制） | 2 | DONE | S1 |
| [S2](./sprint-2-连接/README.md) | ① 连接（数据源/连接器/调度） | 3 | IN_PROGRESS | S1.5 |
| [S3](./sprint-3-集成画布内核/README.md) | ② 集成 · 画布内核 | 2 | DONE | S1 |
| [S4](./sprint-4-集成配置运行/README.md) | ② 集成 · 配置/运行/双视图 | 4 | READY | S3 |
| [S5](./sprint-5-资产/README.md) | ③ 资产（目录/血缘/质量） | 3 | READY | S1 |
| [S6](./sprint-6-指标/README.md) | ④ 指标（指标/语义/dbt 隐藏） | 4 | READY | S4 |
| [S7](./sprint-7-旁路区收尾/README.md) | 旁路区 + 全局搜索 + 打磨 | 4 | READY | S1–S6 |

**统计**: READY=4, IN_PROGRESS=1, DONE=3, BLOCKED=0（S2: F1✅/F2✅/F3 简版；S3 画布内核✅）

## 关键路径

```
S1 地基 ──┬─▶ S2 连接 ──────────────────────┐
          ├─▶ S3 画布内核 ─▶ S4 配置/运行 ─▶ S6 指标 ─┐
          ├─▶ S5 资产 ─────────────────────────────┤─▶ S7 旁路区/收尾
          └────────────────────────────────────────┘
```

S2 / S3 / S5 在 S1 完成后可并行；S6 依赖 S4（画布产物喂指标）；S7 收尾依赖全部。

## 约束基线（全 sprint 适用）

- **Chrome 95**：禁用 oklch / `:has()` / 容器查询 / subgrid；构建必开 `@vitejs/plugin-legacy`（chrome>=95）。
- **mock 契约**：分域 `*Service.ts` 返回 `Promise<Result<T>>`，`VITE_USE_MOCK` 开关；不接真后端。
- **可回植**：组件/token/service 命名对齐现网 `dts-platform-webapp`。
- **设计系统**：Swiss 网格、HSL token、CompactTable 默认 10 条/页。
