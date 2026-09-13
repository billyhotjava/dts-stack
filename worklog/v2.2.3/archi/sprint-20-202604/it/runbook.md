# Sprint-20 Lineage 上线与回滚 Runbook

## 上线前检查

1. 后端编译：

```bash
cd source/dts-platform
./mvnw -q -DskipTests compile
```

2. 前端构建：

```bash
cd source/dts-platform-webapp
pnpm build
```

3. 数据库迁移确认：

```sql
select column_name
from information_schema.columns
where table_name = 'catalog_dataset_lineage'
  and column_name in ('valid_from', 'valid_to');

select indexname
from pg_indexes
where tablename = 'catalog_dataset_lineage'
  and indexname in (
    'uk_catalog_dataset_lineage_current_relation',
    'idx_catalog_dataset_lineage_validity',
    'idx_catalog_dataset_lineage_up_validity',
    'idx_catalog_dataset_lineage_down_validity'
  );
```

4. 回填当前有效期：

```sql
update catalog_dataset_lineage
   set valid_from = coalesce(created_date, last_observed_at, now())
 where valid_from is null;
```

## 上线步骤

1. 部署 `dts-platform`。
2. 部署 `dts-platform-webapp`。
3. 重启后等待 platform health 为 `healthy`。
4. 执行：

```bash
DTS_BASE_URL=http://127.0.0.1:18082 \
DTS_SERVICE_HEADER=dts-ingestion \
worklog/v2.2.3/sprint-20-202604/it/scripts/lineage-e2e-smoke.sh
```

5. 在前端 `/catalog/lineage` 验证：

- 图页能展示 dataset/job/source 节点。
- 表格页能展示字段血缘。
- 快照时间输入后重新加载。
- “时间旅行 Diff”页签能返回新增/移除统计。
- SVG 和 PNG 导出可用。

## 回滚步骤

代码回滚：

1. 回退 `dts-platform` 和 `dts-platform-webapp` 镜像或源码版本。
2. 保留数据库新增列和索引，不删除历史数据。
3. 如果旧代码依赖三元唯一约束，可临时只使用当前边视图：

```sql
select *
from catalog_dataset_lineage
where valid_to is null;
```

数据库回滚原则：

- 不物理删除 `valid_from / valid_to`，避免丢失历史血缘。
- 不恢复全量三元唯一约束，否则历史边和当前边会冲突。
- 如必须回滚到旧唯一约束，先确认没有 `valid_to is not null` 的同源同目标同类型历史边。

## 常见故障

| 现象 | 检查 |
|---|---|
| `impact?at=` 返回空图 | 检查 `valid_from <= at`，以及用户是否有该资产权限 |
| Diff 全为 0 | 检查 `from/to` 是否都在同一有效区间内 |
| 新边写入唯一冲突 | 检查是否仍存在旧 `uk_catalog_dataset_lineage_relation` 约束 |
| 前端 Diff 报时间错误 | 使用浏览器 `datetime-local` 或 ISO8601 UTC，例如 `2026-05-01T07:08:09Z` |
| OpenLineage 无 AIRFLOW 边 | 检查 DAG 中 `DTS_OPENLINEAGE_URL`、`DTS_OPENLINEAGE_ENABLED` 和 platform 内部服务认证 |
