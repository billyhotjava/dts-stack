# 数据开发中心能力评估与改进建议（v2.2.1）

## 1. 评估范围

本次评估覆盖 Platform 中“数据开发中心”相关菜单与后端接口，重点包括：

- 项目空间管理：`source/dts-platform-webapp/src/pages/modeling/ModelTemplatesPage.tsx`
- 逻辑建模：`source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- dbt 文件浏览器：`source/dts-platform-webapp/src/pages/modeling/DbtFileBrowserPage.tsx`
- 即席查询：`source/dts-platform-webapp/src/pages/explore/QueryWorkbenchPage.tsx`
- 脚本开发：`source/dts-platform-webapp/src/pages/explore/etl/ScriptStudioPage.tsx`
- 任务编排：`source/dts-platform-webapp/src/pages/explore/etl/OrchestrationPage.tsx`

后端交叉检查：

- 建模与一键生成：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelingSqlModelResource.java`、`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- dbt 配置与运行：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`、`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtConfigService.java`、`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtDagService.java`
- SQL Workbench：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/sql/SqlWorkbenchResource.java`、`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlExecutionService.java`

---

## 2. 当前能力快照

### 2.1 已具备能力

- 已形成“ODS 接入 -> 逻辑建模 -> dbt 文件微调 -> 触发 dbt run”的主链路。
- 即席查询支持保存查询、执行结果预览、将执行结果沉淀为查询数据集，并可发布到看板中心。
- 项目空间、模型元信息、模型文件写入与字段草稿同步具备基础可用性。

### 2.2 明确短板与风险

1) 功能方向存在冲突（主流程与历史能力并存）
- 页面仍保留“项目包 ZIP 导入”入口：`SqlModelingPage.tsx`（`import-project` 菜单、ZIP 导入弹窗）。
- 后端仍开放 ZIP 导入接口：`POST /api/modeling/sql-models/import-project`（`ModelingSqlModelResource.java`）。
- 与“主流程优先 ODS 一键生成、移除 ZIP 导入”的产品方向不一致，易造成现场使用分裂。

2) 脚本开发与任务编排仍是占位态
- `ScriptStudioPage.tsx` 中脚本列表、模板、运行历史均为空数组，当前仅 UI 骨架。
- `OrchestrationPage.tsx` 仅作为外部链接跳转页，不具备平台内编排能力。
- 数据开发中心菜单对用户是“可见但不可生产”，认知与预期差距大。

3) dbt 运行控制能力偏弱
- 文件浏览器只支持固定触发 `models=all,target=dev`：`DbtFileBrowserPage.tsx`。
- 缺少 compile/test/docs build、模型级 selector、环境级 target 管控、运行前校验。
- `DbtConfigService.java` adapter 仅覆盖 postgres/mysql，对 DM/Oracle/Trino 等后续扩展不友好。

4) SQL Workbench 架构偏“同步执行”，扩展性不足
- `SqlExecutionService.submit(...)` 在请求内直接执行 SQL 并回写结果，长查询易占用 Web 线程。
- 结果处理为“拉全量后截预览”（Gateway 上限 5000 行），缺少服务端分页/游标流式返回。
- `SqlValidationService.java` 目前为字符串级校验（只读限制较弱，防护策略较粗）。

5) 开发中心接口面存在历史债务
- 仍有旧 CRUD 接口直接暴露实体：
  - `/api/saved-queries`：`SavedQueryResource.java`
  - `/api/query-executions`：`QueryExecutionResource.java`
  - `/api/query-workspaces`：`QueryWorkspaceResource.java`
- 这些接口绕过新服务层策略，长期会形成权限、审计与数据一致性风险。

---

## 3. 与商业软件对比（Dataiku/Databricks/dbt Cloud 类）

| 能力域 | 当前状态 | 商业软件常见能力 | 差距 |
|---|---|---|---|
| 主流程收敛 | ODS 一键生成已具备，但仍混入 ZIP 导入旧路径 | 明确单主流程 + 受控导入通道 | 中 |
| 建模开发体验 | 模型 CRUD + 文件浏览器可用 | 语义层、模型依赖可视化、影响分析、批量重构 | 中高 |
| dbt DevOps | 可触发 run，参数能力弱 | run/test/build/docs、环境隔离、CI Gate、Artifacts 追踪 | 高 |
| SQL 开发能力 | 即席查询可用，沉淀数据集可发布 | 异步执行、队列治理、结果缓存、成本治理、细粒度权限 | 中高 |
| 脚本与编排 | 脚本/编排仍是壳层 | 内建任务资产、参数化编排、重跑/补数/SLA/告警 | 高 |
| 安全与治理 | 新链路有审计，旧接口仍并存 | 单入口治理、最小权限、统一审计面 | 高 |

---

## 4. 根因分析

1) 迭代切换期“新旧并存”未完成清理
- 建模链路在从 ZIP 导入向 ODS 一键生成迁移，但 UI/API/文案没有完全收敛。

2) 开发中心多子域并行建设，优先级被主链路挤压
- 脚本开发与编排先落了菜单与入口，后端能力未同步落地。

3) 历史 API 与新 API 并行
- 旧 explore CRUD 控制器未下线，导致治理边界模糊。

4) 执行引擎设计偏“功能打通”
- SQL 与 dbt 先打通功能，尚未进入“可扩展、可治理、可运营”的第二阶段。

---

## 5. 改进建议（P0-P3）

## P0（先收敛主流程与安全面）

- 收敛逻辑建模主流程
  - 下线 ZIP 导入 UI/API，统一为 ODS 一键生成 + 单模型文件导入。
- 清理开发中心历史裸 CRUD 接口
  - 将 `/api/saved-queries`、`/api/query-executions`、`/api/query-workspaces` 迁移或下线。
- 补齐 dbt 运行最小参数化
  - 支持 selector/target 可选，支持 run/test 两类触发。

## P1（补齐“可生产使用”能力）

- SQL Workbench 改为异步任务模型
  - submit 快速返回 executionId，状态轮询 + 服务端分页读取结果。
- 脚本开发 MVP 落地
  - 脚本资产 CRUD、版本、执行记录与日志打通。
- 编排能力从“外链跳转”升级为“平台内可观测入口”
  - 展示 DAG 列表、最近运行、失败重试、跳转上下文。

## P2（提升治理与工程化）

- dbt DevOps 能力补齐
  - compile/test/docs、运行前校验、产物同步状态可视化。
- 建模与数据质量联动
  - 一键生成后自动附带基础测试模板（not_null/unique/freshness）。
- 语义层与指标层对齐
  - 从“SQL 模型管理”升级到“模型 + 指标 + 看板”的一致契约。

## P3（对齐商业化体验）

- GitOps/CI 门禁
  - PR 触发 dbt compile/test；发布前强制质量门禁。
- 多环境与多架构一致性
  - x86/ARM + legacy/normal/dev 全矩阵回归自动化。
- 运营指标体系
  - 开发效率、失败率、MTTR、发布成功率、质量缺陷闭环看板。

---

## 6. 结论

数据开发中心目前处于“核心链路可用，配套能力不足”的状态。

- 优先要做的是 P0：收敛主流程、消除历史接口面、补齐最小运行参数化。
- 完成 P1 后，开发中心才能从“演示可用”进入“生产可用”。
- P2/P3 再逐步对齐商业软件在治理、质量与工程化方面的能力。
