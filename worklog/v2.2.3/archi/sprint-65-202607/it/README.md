# Sprint-65 集成测试与交付证据计划

**状态**：IN_PROGRESS
**说明**：本文件既保留验收合同，也记录已执行检查点；未列入执行记录的场景仍视为未完成。

## 1. 验收环境

| 环境 | 用途 | 最低要求 |
|---|---|---|
| Backend unit/contract | 领域不变量、API、迁移 | PostgreSQL 测试库、租户和权限夹具 |
| Frontend build | TS、source-contract、构建 | 与当前 pnpm lock 一致 |
| Compose integration | 全链路 API、dbt、运行证据 | v2.2.3 compose，禁止复用生产数据 |
| Chrome 95 | 客户浏览器兼容 | 用户名密码自动登录、远程 IP 可访问 |
| Migration rehearsal | 存量计划和模型迁移 | 脱敏快照、dry-run、可重跑 |

## 2. 集成场景矩阵

| ID | 场景 | 前置条件 | 核心断言 | 关联 Feature |
|---|---|---|---|---|
| IT-01 | 业务驱动创建计划 | 有连接、域和过程权限 | 生成 canonical plan，默认进入业务范围，基线门禁真实 | F2/F3 |
| IT-02 | 资产驱动 Excel/ODS | 有 Excel 或 ODS 元数据 | 默认进入来源盘点，候选需确认，与 IT-01 同一状态机 | F3 |
| IT-03 | 来源映射不完整 | 至少一个来源未映射 | 基线不能确认，工作台显示唯一修复动作 | F3/F5 |
| IT-04 | 经典事实/维度设计 | baseline READY | 粒度、事实形态、时间语义一致，关系可派生 | F4 |
| IT-05 | 标准漂移 | 已绑定旧标准版本 | 投影为 BLOCKED/STALE，不能静默发布 | F4/F5 |
| IT-06 | 设计器生成模型 | DESIGNER_GENERATED | 可生成实现产物，dbt 不能静默覆盖 | F6 |
| IT-07 | dbt 管理模型 | DBT_MANAGED + manifest | import 为候选，确认后回写 artifact/lineage/test/run | F6 |
| IT-08 | 发布失败修复 | test 或 run 失败 | 从工作台到模型/运行定位，修复后证据刷新 | F5/F6 |
| IT-09 | 存量计划迁移 | 有两套旧计划记录 | dry-run、幂等、映射和冲突清单完整 | F2/F7 |
| IT-10 | 旧路由和权限 | 旧菜单角色绑定 | 深链不丢 planId，权限不扩大，能返回同一计划 | F7 |
| IT-11 | tenantId 作用域隔离 | 测试夹具注入两个 tenantId 且存在同名 code | UI 不接收 tenantId；仓储、唯一键、引用和迁移按服务端 tenantId 完全隔离 | F2/F8 |
| IT-12 | 编辑单元级乐观锁 | 两用户编辑相同或不同编辑单元，并并发切换生命周期 | 同一单元冲突 409；不同单元均成功；状态转换竞争不产生部分完成 | F2/F8 |
| IT-13 | 专业模块不可用 | 标准或资产 API 暂时失败 | 显示 UNKNOWN/STALE，不误报完成 | F5/F8 |
| IT-14 | 归档与恢复查看 | 已发布计划 | 归档保留成果和证据，只读可追溯 | F2/F8 |
| IT-15 | Chrome 95 双起点 | 可自动登录 | 页面无语法错误，关键操作和恢复路径可用 | F3-F8 |
| IT-16 | 三种视图单状态源 | 同一 planId 有九站 StageProjection | 八阶段总览、九站工作台和六 Tab 映射一致；URL/session 不产生完成状态 | F5/F7/F8 |

## 3. 分层验证命令合同

具体命令以受影响模块的 `package.json`/`pom.xml` 为准，实施时至少执行：

```text
source/dts-platform:
  targeted unit/contract tests
  backend:unit:test 或等价 Maven 测试

source/dts-platform-webapp:
  source-contract tests
  pnpm exec tsc --noEmit
  pnpm build

repository:
  git diff --check
  GitNexus detect changes
  Liquibase empty-db + upgrade-db rehearsal
  Chrome 95 Playwright smoke
```

不得为了通过验证降低现有测试覆盖、跳过失败测试或把测试状态写死。

## 4. 数据迁移验收

| 检查项 | 通过条件 |
|---|---|
| dry-run | 无写入，生成计划/版本/引用/冲突预览 |
| 行数 | 每条旧计划有迁移、合并或人工冲突结果 |
| 幂等 | 重跑不产生重复 plan、binding 或 version |
| 引用 | ModelSpec、review、version 和菜单深链可追溯 |
| 冲突 | 不自动覆盖，输出人工决策清单 |
| 回滚 | 恢复旧读取入口但保留 canonical 新数据 |
| 审计 | 迁移批次、执行人、时间、校验和完整 |

## 5. Chrome 95 验收重点

- 计划列表、新建选择和六阶段详情可用；
- 顶部无多重说明卡堆叠，页面有且仅有一个主动作；
- 切换 Tab、刷新、复制深链后 `planId` 保持；
- 工作台不会跳到主题域管理；
- 错误、空态、加载态和权限态客户可理解；
- dbt 高级入口不出现在普通规划的必经步骤；
- 旧路由 redirect 无循环、白屏或权限丢失。
- `/workbench` 八阶段状态来自九站 StageProjection 映射，计划详情六 Tab 不自持完成度。

## 6. 证据清单

实施完成时必须补充：

- `test-results/`：JUnit、前端测试、Playwright 报告；
- `screenshots/`：双起点、当前阻塞、模型中心、dbt、失败修复；
- `migration/`：dry-run、差异、幂等和回滚报告；
- `api/`：关键请求/响应脱敏样例；
- `runtime/`：compose 服务状态和健康检查；
- 本 README 的执行日期、commit、环境和结论。

## 7. Go / No-Go

以下任一发生即 No-Go：

- 存在两个可写计划事实源；
- 任一起点绕过统一 baseline 门禁；
- stage 完成来自前端缓存或访问顺序；
- dbt 能无提示覆盖设计器模型；
- 旧计划、模型、权限或深链出现不可解释丢失；
- 迁移不可幂等或回滚会删除 canonical 数据；
- Chrome 95 主流程白屏或无法完成关键动作；
- 核心代码出现行业专属分支。

## 8. 2026-07-18 实施检查点

| 层级 | 命令/范围 | 结果 |
|---|---|---|
| Backend contract | `./mvnw -ntp -Dtest='WarehousePlan*,ModelingVNext*' test` | 35 tests，0 failure，PostgreSQL 集成测试通过 |
| Backend package | `./mvnw -ntp -DskipTests package` | BUILD SUCCESS |
| Frontend source contract | `node --test`：WarehousePlan 工作台、六 Tab、旅程收敛、视图模型、个人工作台 | 25 tests，0 failure |
| Frontend existing contract | 受影响建模/工作台/菜单 source-contract 集 | 53 tests，0 failure |
| Frontend type/build | `pnpm exec tsc --noEmit --pretty false`、`pnpm build` | 均通过；legacy browser build 成功 |
| Chrome 95 静态护栏 | 新增/修改前端文件扫描禁用语法 | 未发现 `:has`、container query、现代数组复制 API 等禁用项 |
| Repository | `git diff --check` | 通过 |
| Runtime migration | v2.2.3 PostgreSQL `databasechangelog` | `20260718-01a` 至 `01d` 四个 changeset 均为 `EXECUTED` |
| Runtime health | 重建并滚动重启 `dts-platform`、`dts-platform-webapp` | 后端 `healthy`，前端运行；用户名密码登录可用 |
| Browser smoke | Headless Chrome 150，1366x768 与 390x844 | BUSINESS_FIRST 进入业务范围；ASSET_FIRST 进入来源盘点；九站可见；新页面 console error=0，WarehousePlan API=200 |

浏览器证据：

- [ASSET_FIRST 计划详情](screenshots/sprint65-asset-first-plan.png)
- [九站 StageProjection 工作台](screenshots/sprint65-nine-stage-workbench.png)
- [390x844 窄屏工作台](screenshots/sprint65-workbench-narrow-390x844.png)

浏览器验证中发现并修复两项真实契约漂移：后端英文缺证据文案不再直接暴露给客户；资产起点枚举由错误的 `DATA_FIRST` 收敛为后端 canonical `ASSET_FIRST`。两条验收计划验证后已调用 archive API 归档，未删除数据。

当前环境仅安装 Chrome 150，没有可执行的 Chrome 95 二进制。因此本检查点包含 legacy browser production build、Chrome 95 禁用语法扫描和双视口浏览器回归，但**不冒充 Chrome 95 真机通过**；F8-T02 继续保持 `READY`。

当前检查点只证明 canonical 聚合、基线、九站投影、双起点工作台、六 Tab 和 R1 兼容层已经形成可构建闭环。F2 的存量迁移/版本评审、F3/F4/F6、全部 IT-01 至 IT-16 以及完整 Chrome 95 端到端仍未完成，因此 Sprint 保持 `IN_PROGRESS`，不得据此宣告 Go-Live。
