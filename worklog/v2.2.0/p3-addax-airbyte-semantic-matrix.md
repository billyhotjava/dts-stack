# DTS v2.2.0 P3 Addax -> Airbyte 语义差异矩阵

## 1. 范围
- 当前执行引擎：Addax（单机/Compose）
- 目标执行引擎：Airbyte（K8s）
- 对比维度：全量、增量、CDC、重试、幂等、回填、观测

## 2. 差异矩阵
| 维度 | Addax（当前） | Airbyte（目标） | 兼容策略 |
| --- | --- | --- | --- |
| 全量 | 任务侧控制（DROP/CREATE/TRUNCATE + 写入） | Connector + destination mode 组合 | 平台统一语义：`FULL`，由编排层下发执行模式 |
| 增量 | 任务配置水位列/类型，平台维护 checkpoint | Connector 原生 cursor/state | 平台保留统一 checkpoint 审计，Airbyte state 做底层细节 |
| CDC | 非通用（依赖源端能力） | 多 connector 原生支持 | 平台能力层先暴露 `CDC` 能力，再按 connector 灰度放开 |
| 重试 | 任务级重试（FAILED_ONLY/FULL_RERUN） | Job 级 retry + sync reset | 平台继续暴露统一重试策略，落地到引擎映射 |
| 幂等 | 依赖 preSql/writeMode 与目标表策略 | destination sync mode + normalization | 平台保留“任务语义幂等”，引擎仅执行 |
| 回填 | 手工窗口重跑 | 可通过参数化 sync + backfill | 在能力层显式标记 `BACKFILL` 并定义窗口参数 |
| 观测 | Airflow + Addax 日志聚合 | Airbyte API + metrics | 平台统一执行状态模型与错误码 |

## 3. 映射约定
- `FULL` -> Addax full_refresh / Airbyte full refresh
- `INCREMENTAL` -> Addax watermark / Airbyte cursor-based
- `CDC` -> 仅当 connector capability 含 `CDC` 才可用
- `BACKFILL` -> 平台指定窗口参数，底层引擎按 connector 语义展开

## 4. 降级策略
- connector 未声明某能力时，前端禁用对应模式并提示。
- `CDC` 不可用时自动降级为 `INCREMENTAL` 或 `FULL`（按任务配置）。
- 平台审计侧保留统一字段，避免切换引擎导致历史不可比。

## 5. 当前落地状态
- 已落地连接器能力配置表与 API。
- 已在任务创建页面展示能力标签，并限制增量模式可选性。
- 已新增实时状态接口与页面预留展示位。
