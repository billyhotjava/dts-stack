# 建模 vNext API 矩阵

| 方法 | 路径 | 用途 | 前端消费者 | 后端责任 |
|---|---|---|---|---|
| GET/POST | `/api/modeling/vnext/business-objects` | 业务对象台账 | 业务对象台账 | 对象、粒度、主键校验 |
| GET/POST/PUT | `/api/modeling/vnext/plans` | 数仓规划 | 规划入口、向导 | 规划状态与上下文 |
| GET/POST/PUT | `/api/modeling/vnext/model-specs` | ModelSpec 台账 | 模型台账、向导 | 版本、模式、关联关系 |
| GET | `/api/modeling/vnext/model-specs/{id}/dependencies` | 查询模型依赖 | 模型详情 | 按 `dependsOn` 返回上游 ModelSpec |
| POST | `/api/modeling/vnext/model-specs/{id}/compile` | 编译 dbt 产物 | 模型详情 | 生成 SQL/schema/tests/docs |
| POST | `/api/modeling/vnext/dbt/import` | 导入 manifest/SQL | 高级开发入口 | 登记 dbt 原生模型 |
| GET | `/api/modeling/vnext/model-specs/{id}/drift` | 查看 SQL/规格漂移 | 模型详情 | 字段、来源、粒度差异 |
| POST | `/api/modeling/vnext/runs` | 触发运行 | 运行入口 | 投递 Airflow |
| GET | `/api/modeling/vnext/runs/{id}` | 运行证据 | 运维页 | 汇总 Addax/dbt/Airflow 状态 |
| GET | `/api/modeling/vnext/lineage/{id}` | 语义血缘 | 模型详情、资产详情 | 返回上下游与 dbt unique_id |

业务过程仍由现有语义目录提供；vNext 通过 `processId` 绑定业务对象、规划和 ModelSpec。`/api/modeling/vnext` 是新版本命名空间，避免与旧 `/api/modeling` 规划接口冲突。

旧 `/api/semantic/*` 继续作为兼容面，不新增 `/v2` URL 前缀。

写请求统一携带 `revision` 与 `idempotencyKey`。revision 不匹配返回 `MODEL_REVISION_CONFLICT`，重复幂等键不得生成新 revision；校验失败返回稳定错误码和 `issues` 数组。
