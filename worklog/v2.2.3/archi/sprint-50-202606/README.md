# Sprint-50: 数据标准与 dbt 模型契约联动设计

**时间**: 2026-06  
**状态**: IMPLEMENTED  
**类型**: Architecture Design + Frontend-first Product Matrix + Implementation  
**目标**: 先完善和重构现有前端页面承载面，再把数据标准和 dbt 模型挂钩，形成“标准定义 -> 模型开发 -> dbt 校验 -> 发布门禁 -> 资产同步”的数据开发中心闭环。默认不新增菜单或页面。

## 背景

Sprint-48 已固化“现有前端页面为第一事实源”的重构规则，Sprint-49 已完成首批客户可见页面闭环整改。下一步优化聚焦数据开发中心：当前标准管理、逻辑建模、dbt 文件浏览已经存在，但页面承载面还没有先为“标准到模型”的工作流准备好，数据标准与 dbt 模型之间也缺少明确的字段级绑定、生成物契约和发布门禁。

本 sprint 必须先执行 F0 前端页面承载面完善与重构，再进入标准/dbt 契约联动。后续进入实现时，所有按钮、组件、接口和测试必须从本设计拆分 feature/task。

## 现有页面承载

| 页面 | 路由 | 当前角色 | Sprint-50 设计定位 |
|------|------|----------|--------------------|
| 业务术语 | `/governance/standards/glossary` | 维护业务口径和术语 | 作为模型描述、指标语义、字段解释的业务语言来源 |
| 数据元 | `/governance/standards/elements` | 维护字段标准、类型、码表、安全等级 | 作为 dbt column contract 的字段级标准来源 |
| 公共码表 | `/governance/standards/reference` | 维护码表项和映射，已有“更新 dbt Seeds”按钮 | 作为 dbt seeds 与 accepted values 校验来源 |
| 逻辑建模（SQL） | `/studio/sql-modeling` | 管理 SQL 模型、编译、测试、构建、发布 | 作为标准映射、dbt yaml 生成、质量门禁的主工作台 |
| 项目文件浏览 | `/modeling/dbt-files` | 浏览、编辑、运行 dbt 项目文件 | 作为底层 dbt 文件证据面，不作为业务主流程入口 |
| 任务编排 | `/explore/etl/orchestration` | Airflow/任务运行查看 | 承接 dbt build/release 后的运行证据 |

## Feature 候选

| ID | Feature | 优先级 | 状态 | 说明 |
|----|---------|--------|------|------|
| F0 | 前端页面承载面完善与重构 | P0 | DONE | 已先完成页面承载面、路由、按钮和状态整理 |
| F1 | 标准到模型的页面矩阵与契约定义 | P0 | DONE | 已固化页面矩阵、后端 DTO、前端 API 和 source-contract |
| F2 | SQL 建模页字段标准映射 | P0 | DONE | SQL 建模页支持自动匹配数据元并保存字段标准绑定 |
| F3 | 公共码表到 dbt Seeds 联动增强 | P0 | DONE | 公共码表页保留 Seeds 同步，模型字段绑定引用 `codeSet` |
| F4 | dbt schema.yml 与标准元数据生成 | P0 | DONE | 后端生成并写入模型同目录 `schema.yml` |
| F5 | 发布门禁接入标准校验 | P0 | DONE | 后端标准门禁接口和前端检查按钮已接入 |
| F6 | Chrome95 与 source-contract 验收 | P0 | DONE | source-contract、Chrome95 构建、Playwright 桌面/窄屏 smoke 已补齐 |

## 完成标准

- [x] F0 已完成，且数据开发中心相关页面具备承载标准/dbt 联动的页面结构、按钮状态、空态、错误态和路由交接。
- [x] 不新增菜单或页面，优先复用标准管理、逻辑建模和 dbt 文件浏览。
- [x] 数据元能够在 SQL 模型字段上体现为标准绑定、标准版本和漂移状态。
- [x] 公共码表能够生成或更新 dbt seeds，并在模型字段校验中被引用。
- [x] dbt `schema.yml` 能表达 DTS 标准元数据，不只停留在前端展示。
- [x] 发布门禁能够检查标准映射、码表同步、dbt 测试和下游影响。
- [x] 所有新增按钮都有路由、接口、禁用原因、失败态和 source-contract 测试。
- [x] Chrome 95 下使用普通表格/抽屉/选择器，不依赖现代浏览器特性。（构建与浏览器 smoke 已补齐）

## 实施记录

| 日期 | 内容 | 证据 |
|------|------|------|
| 2026-06-19 | 后端新增字段标准绑定、schema.yml 生成、标准门禁接口 | `ModelingSqlModelServiceTest#saveStandardBindings_shouldPersistBindingsInSemanticContractAndGenerateDbtSchemaYml` |
| 2026-06-19 | 前端 SQL 建模页接入自动匹配数据元、标准门禁、生成 schema.yml | `dataDevelopmentWorkbench.source-contract.test.ts` |
| 2026-06-19 | Chrome95 兼容生产构建 | `pnpm build` |
| 2026-06-19 | Playwright 验证 SQL 建模页标准绑定区域 | `it/screenshots/playwright-sql-modeling-mocked-smoke.json` |

## 资产

- 架构设计: `assets/data-standards-dbt-modeling-architecture.md`
- Feature 台账: `features/`
