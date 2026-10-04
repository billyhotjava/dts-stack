# Sprint-20 历史血缘回填 Runbook

## 目标

将存量 ODS 映射、dbt manifest 和 OpenLineage 事件补写到 `catalog_dataset_lineage`，并确保所有当前边都有 `valid_from`。

## 步骤

1. 回填现有边有效期：

```sql
update catalog_dataset_lineage
   set valid_from = coalesce(created_date, last_observed_at, now())
 where valid_from is null;
```

2. 回填 Addax 入湖血缘：

```bash
curl -fsS -X POST \
  -H "Authorization: Bearer <token>" \
  "$DTS_BASE_URL/api/catalog/lineage/sync-addax"
```

本地内部验证可用：

```bash
curl -fsS -X POST \
  -H "X-DTS-Service: dts-ingestion" \
  "http://127.0.0.1:18082/api/catalog/lineage/sync-addax"
```

3. 回填 dbt 血缘：

- 通过现有 dbt manifest 自动同步任务执行。
- 或在前端 `/catalog/lineage` 使用“导入 dbt 血缘”上传 manifest。

4. 验证当前边：

```sql
select relation_type, verification_status, count(*)
from catalog_dataset_lineage
where valid_to is null
group by relation_type, verification_status
order by relation_type, verification_status;
```

5. 验证时间旅行：

```bash
DATASET_ID=<dataset-id>
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
curl -fsS \
  -H "Authorization: Bearer <token>" \
  "$DTS_BASE_URL/api/catalog/lineage/diff?datasetId=$DATASET_ID&from=2020-01-01T00:00:00Z&to=$NOW&direction=BOTH&depth=3"
```

## 回填原则

- 自动来源边不做物理删除，失效时写 `valid_to`。
- 同一来源类型只 upsert 当前边，不覆盖其他来源类型。
- `KNOWN_UNVERIFIED` 表示已知来源关系但尚未由成功执行验证，不等于错误血缘。
