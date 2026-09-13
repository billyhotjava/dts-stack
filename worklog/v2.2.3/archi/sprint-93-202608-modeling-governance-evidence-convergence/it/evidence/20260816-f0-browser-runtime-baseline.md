# F0 浏览器与运行态基线证据（2026-08-16）

**环境**：本地 v2.2.3 运行栈，`https://bi.yuzhicloud.com`
**操作者**：xiezm
**浏览器**：本地 Playwright 接管的 Chrome 150；不是 Chrome 95
**边界**：只读登录、页面、API、SQL、migration preview 和依赖探针；未执行 apply、业务写入、构建或最终 E2E

## 1. 登录与权限

- 从真实登录页进入原始受保护目标 `/bi/card/new`。
- `/api/session/status` 返回 200，`authenticated=true`，用户名为 xiezm，角色包含 `ROLE_INST_DATA_OWNER`。
- 本证据不记录密码、Cookie、token、会话过期值或客户端地址。

## 2. 真实页面 smoke

所有页面均通过完整 reload 后按真实路由确认，避免只修改 hash 导致旧组件残留。

| 页面 | 路由 | 结果 |
|---|---|---|
| 模型工作台 | `/data-modeling/dimensions/workbench` | 可达；模型 workbench 及其依赖 API 均 200 |
| 资产概览 | `/catalog/assets` | 可达；overview API 200；页面显示资产总量 200 |
| 数据资产目录 | `/catalog/search` | 可达；页面显示 286 条 |
| 元数据管理 | `/catalog/metadata-management` | 可达；页面显示共 365 条 |
| 血缘图谱 | `/catalog/lineage/graph` | 可达；表级图可展示，明确提示当前无字段血缘 |
| 质量管控 | `/governance/rules` | 可达；质量大盘加载成功 |
| 质量报告 | `/governance/quality` | 可达；当前资产无有效检测结果的空态可解释 |

- 质量报告干净重载后 console error=0。
- session、menu、默认目标、quality rules/datasets/score 网络请求均为 200。
- 模型工作台干净重载后 console error=0；warehouse plan、domain tree、ModelSpec、dimension definition、metadata standard、data mart、subject domain、warehouse layer、materialization 和 workbench 请求均为 200。
- 截图：[`20260816-xiezm-asset-overview.png`](20260816-xiezm-asset-overview.png)。

## 3. 受保护 API

在同一登录会话中直接调用：

| API | HTTP | 响应契约 |
|---|---:|---|
| `/api/catalog/assets-v2?page=0&size=1` | 200 | 标准 `{status,message,code,data}` |
| `/api/catalog/assets-v2/stats-projection` | 200 | 标准 `{status,message,code,data}` |
| `/api/catalog/asset-normalization-migrations/preview?limit=100` | 200 | preview + rows + totalPending |

## 4. 迁移 preview

- 连续两次 preview 的 hash 均为 `f336327365a4ffebca6357fb20573c3f0030a500f5b5842d225dcaeb7f446c52`。
- `totalPending=3`、`rowCount=3`、`truncated=false`。
- 三条均为 SOURCE，`automatic=true`、`issueCode=null`。
- 未调用 apply/rollback；运行库业务记录未被修改。

## 5. 数据与 Schema

| 指标 | 结果 |
|---|---:|
| `catalog_dataset` | 367 |
| `catalog_asset_semantic_projection` | 0 |
| `modeling_catalog_model_serving_projection` | 43，全部 SYNC_PENDING |
| 当前表级血缘 | 53 |
| 当前字段级血缘 | 0 |
| `gov_quality_run` | 119，全部 FAILED，仅 1 个 dataset |

Liquibase 已执行：

- `20260810-02-catalog-asset-semantics-projection`
- `20260811-01-platform-model-governance-policy`
- `20260811-02-seed-platform-model-governance-policy`
- `20260811-03-model-release-candidate-implementation-snapshot`
- `20260814-01-model-release-candidate-self-service`

## 6. 外部依赖

- dts-platform：running/healthy。
- dts-platform-webapp：running。
- Airflow webserver：running/healthy，`/health`=200。
- OpenMetadata：running，`/api/v1/system/version`=200；DTS OM cache/mapping 仍为 0。
- dbt：running，dbt-core 1.10.22。
- Kafka：running/healthy。

## 7. 尚未闭合

- Chrome 95、四态、部门越权负向和写命令未执行。
- 尚无 `E2E_GOV_202608_*` 字段血缘链和 pass/fail/expired 治理质量样本。
- 概览 200、目录 286、数据库 367 的口径不一致仍待 F1/F5 收敛。
- OM/字段血缘/服务投影的端到端集成尚未闭合。
