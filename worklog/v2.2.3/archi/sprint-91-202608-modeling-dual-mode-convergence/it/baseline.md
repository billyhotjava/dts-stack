# 交付基线探针结果 (Gate G0)

**探针日期**: 2026-08-13
**环境**: 当前 v2.2.3 Docker 运行实例
**结论**: GAP——运行、数据库、聚焦后端、前端类型检查和兼容构建已通过；真实登录、Chrome 95、真实库 ownership transition/compile、手工全链路与显式命令发布仍待关闭，且既有 release candidate 回归存在 1 条语义冲突。

> P9 于架构复核后补入。它只验证 Sprint-91 的前向接管链，不再承担回切范围判断。

| # | 探针 | 结果 | 证据 | 阻断项 |
|---|---|---|---|---|
| P1 | 可运行实例 | PASS | `v223-dts-platform-1`、webapp、Keycloak、dts-dbt、PostgreSQL 均运行；platform 容器 healthy | - |
| P2 | 登录/鉴权路径 | GAP | 本轮未使用真实账号进入受保护模型工作台 | F0/T01 |
| P3 | Schema 状态 | PASS | 运行库存在 model spec / implementation / draft / artifact / release candidate 全套表 | - |
| P4 | 代表性数据 | PASS_WITH_GAPS | 31 个 v2 模型、28 DBT + 3 DESIGNER；需新建可回收的 DESIGNER 转换样本，避免修改唯一 DRAFT 样本 | F0/T01 |
| P5 | API 验收 harness | PASS_WITH_GAPS | platform 容器内 `GET http://127.0.0.1:8081/management/health` → `status=UP`；同源受保护 `/api/management/health` 未登录返回 401；当前主机无法解析 `api.yuzhicloud.com`，modeling API 仍待登录 token | F0/T01 |
| P6 | UI 验收 harness | GAP | `https://bi.yuzhicloud.com/`（本机 resolve）→ HTTP 200；尚未完成登录、截图、console/network 检查 | F0/T01 |
| P7 | 构建与测试命令 | PASS | modeling 后端 10 类 147/147；审计字典 5/5；前端 Vitest 5 文件 15/15、`tsc --noEmit`、`pnpm build` 全部通过 | - |
| P8 | 外部依赖 | PASS | `dts-dbt`、Keycloak、PostgreSQL 容器均运行；本 Sprint 不新增第三方依赖 | - |
| P9 | 编译产物与 canonical bundle 实测 | PASS_WITH_GAPS | 编译输出 4 种 artifact type 聚合为 3 文件；4 文件 project 经 literal-only 校验、freeze/restore；transition service 测试断言只导入 `{SQL,SCHEMA,CONFIG}`，compiler 测试确认该集合通过门禁。仍缺真实 PostgreSQL 事务失败注入和登录态样本 | F0/T02、F2/T02 |
| P10 | 手工全链路与真实 ODS | GAP | 规范链和样本契约已写入 F0/T03；尚未记录真实 ODS source binding ID/resolved version/行数，未通过 UI 创建 DWD DIM/FACT、DWS、ADS | F0/T03 |

## 集中自动化结果

- 后端 modeling：147/147 PASS；覆盖 validator、capability、representation、compiler、transition service/resource/repository、ModelSpec 与 lifecycle。
- 审计资源：`AuditActionCatalogResourceTest` 5/5 PASS；三份 runtime catalog mirror 内容一致。
- 前端：Vitest 5 files / 15 tests PASS；TypeScript exit 0；legacy browser production build exit 0。
- 构建分包：`DbtCodeEditor` 与 `configureMonaco` 独立 chunk；仍需浏览器 network 证明 `view=visual` 不请求 Monaco。
- 发布候选：80 tests / 79 PASS / 1 FAIL。失败为既有 `BUILT → CANCELLED` 状态机允许、旧测试期望禁止的语义冲突；本 Sprint 未修改相关源文件。
- Chrome 95 静态扫描只命中 `crypto.randomUUID`；应用入口的 `legacy-browser` polyfill 已覆盖该 API，新增幂等 helper 仍提供隔离嵌入/测试 fallback。真实 Chrome 95 smoke 未执行。

## F0/T01 关闭条件

1. 用真实维护账号进入 `/data-modeling/dimensions/workbench`，确认能打开一个 DBT_MANAGED 模型；再用只读账号确认写动作不可用。
2. 新建三个 `E2E_S91_*` 可回收模型：两个 DESIGNER_GENERATED（分别用于原生发布与接管）、一个 DBT_MANAGED，记录 ID 和初始 revision/checksum。
3. 使用 `xiezm` 验证所级数据管理员在当前 command guard 下的显式维护/生命周期动作；使用部门只读或受限账号验证所属范围与越界 403。即使同一 actor 被授权多个 duty，也禁止前端自动审批或自动发布。
4. 确认现有 Chrome 自动化可访问页面；最终验收使用 Chrome 95 兼容构建和目标浏览器 smoke。
5. 运行一次当前基线的前端 source-contract/Vitest 与 `pnpm build`，后端运行 modeling focused tests，证明确有可用 RED→GREEN 路径。

## F0/T02 关闭条件（P9）

1. 仅对 `E2E_S91_DESIGNER_*` 执行编译，记录 `ArtifactWrite` 类型集合、路径和内容 checksum；SQL 正文不得写入 worklog。
2. 将 3 个生成文件与确定性 `dbt_project.yml` 组成 4 文件 project，调用既有静态校验与 freeze/restore，断言项目身份、文件集合和 checksum。
3. 按修订后契约写入 DBT_MANAGED 制品后调 `compile`，断言不抛 `MODEL_DBT_IMPORT_REQUIRED`、CONFIG 内含 4 文件、selector 不变。
4. 探针结论回写 Sprint README 的复核结论 A 与 ADR-91-04；回切调查留在 Sprint-92 backlog。

## F0/T03 关闭条件（P10）

1. 选择已有且含测试数据的 ODS source binding，只读记录 ID、resolved version、字段状态和行数；不执行 DDL/TRUNCATE/装数。
2. 通过真实 UI 创建 `E2E_S91_CHAIN_*` 的 DWD DIM、DWD FACT、DWS、ADS；FACT 同时保存基础来源和 DIM 固定修订。
3. 记录 model/implementation/dependency revision/checksum，作为 F6～F8 与 F5 唯一共享样本。
4. 样本失败时保持 GAP，不用 ZIP、数据库脚本或私有 API 替代 UI 步骤。

## 本 Sprint 验收路径

- 后端：ownership transition MockMvc/service IT + dbt draft/lifecycle/release focused tests。
- 前端：source-contract + Vitest 组件测试；编码全部结束后只运行一次集中浏览器 E2E。
- 浏览器：登录 → 数据建模 → 双模式/显式接管 → 手工与可视化全链路 → 单表/批量/二次物化 → 构建/质量/显式评审/发布 → 治理证据。
- 证据：统一写入本目录 IT-01～IT-16，不使用占位截图。
