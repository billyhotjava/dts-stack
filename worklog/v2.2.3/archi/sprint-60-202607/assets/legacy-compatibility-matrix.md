# Modeling vNext 旧资产兼容矩阵

| 资产 | 新版本登记方式 | 可读 | 可运行 | 可写/覆盖 | 回滚边界 |
|---|---|---:|---:|---:|---|
| 旧 `/api/semantic/*` | 保留原路径，作为兼容视图 | 是 | 是 | 按原权限 | 新 API 失败时回到旧页面，不删除旧数据 |
| 旧 dbt SQL / manifest | `legacyRef` + `LEGACY_READONLY` | 是 | 是 | 否 | 新 ModelSpec 发布失败只回滚新 revision，不回写旧 SQL |
| 新 `ModelSpec`（设计器） | `/api/modeling/*` 登记 | 是 | 是 | 是 | 按 revision 回滚到上一个已审核版本 |
| 新 `ModelSpec`（dbt 原生） | manifest 登记，SQL 为事实源 | 是 | 是 | 仅 dbt 工作区 | 删除登记不删除 dbt 文件，允许重新导入 |

## 不变量

- 导入旧模型只能新增登记与血缘，不得修改或删除原始 dbt 文件。
- `LEGACY_READONLY` 资产允许查看、运行、重新登记，不允许从设计器覆盖。
- 新旧 API 不共享写入 DTO；兼容层只做响应映射，避免新字段反向污染旧页面。
- 发布失败时只撤销当前 ModelSpec revision、生成物和运行登记，保留旧模型和旧运行证据。
