# Sprint-20 Lineage 性能压测报告

## 范围

压测对象：

- `/api/catalog/lineage/impact`
- `/api/catalog/lineage/impact?withJobs=true`
- `/api/catalog/lineage/impact?withColumns=true`
- `/api/catalog/lineage/diff`

## 本地基线

2026-05-01 本地库当前血缘规模：

```text
catalog_dataset_lineage total=146
current_edges=146
relation types: ADDAX / AUTO_VIEW / DBT / AIRFLOW / MANUAL
```

在该规模下完成接口烟测，响应可正常返回。当前库未达到 500/1000/2000 边大图规模，因此大图 P95 仍需在压测数据集补齐后归档。

## 压测方法

生成大图数据后执行：

```bash
BASE=http://127.0.0.1:18082
DATASET_ID=<center-dataset-id>
AUTH=(-H "Authorization: Bearer <token>")

for depth in 1 2 3 5; do
  hey -z 60s -c 20 "${AUTH[@]}" \
    "$BASE/api/catalog/lineage/impact?datasetId=$DATASET_ID&direction=BOTH&depth=$depth&withJobs=true&withColumns=true"
done

hey -z 60s -c 20 "${AUTH[@]}" \
  "$BASE/api/catalog/lineage/diff?datasetId=$DATASET_ID&direction=BOTH&depth=3&from=2020-01-01T00:00:00Z&to=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
```

## 达标线

| 场景 | 目标 |
|---|---:|
| 500 边，depth=3，withJobs=false | P95 < 1500 ms |
| 500 边，depth=3，withJobs=true | P95 < 2500 ms |
| 1000 边，depth=3，withJobs=true | P95 < 4000 ms |
| 2000 边，depth=3，withJobs=true | P95 < 7000 ms |
| Diff 1000 边 | P95 < 5000 ms |

## 已落性能保护

- `depth` 限制在 1 到 10。
- 快照查询走 `valid_from / valid_to` 索引。
- 当前边唯一性改为 `valid_to is null` 部分唯一索引，避免历史边阻塞 upsert。
- 前端默认深度为 3，列级血缘作为可选加载。

## 待归档

- 构造 500/1000/2000 边数据集。
- 归档 `hey` 输出、数据库 explain analyze 和前端截图。
