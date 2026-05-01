# Sprint-20 Lineage 端到端冒烟

## 目标

验证 Sprint-20 的主闭环：Addax/DBT/手工/OM 技术血缘进入统一 lineage API，前端可展示 job 节点、字段血缘、快照和 Diff。

## 执行命令

```bash
DTS_BASE_URL=http://127.0.0.1:18082 \
DTS_SERVICE_HEADER=dts-ingestion \
DTS_SMOKE_OUT=worklog/v2.2.3/sprint-20-202604/it/evidence/$(date +%Y%m%d)-local \
worklog/v2.2.3/sprint-20-202604/it/scripts/lineage-e2e-smoke.sh
```

生产或浏览器会话环境推荐改用 `DTS_TOKEN` 或 `DTS_COOKIE`，不要使用内部服务头。

## 验收步骤

| Step | 验证点 | 输出 |
|---|---|---|
| 1 | `impact?withJobs=true&withColumns=true` 返回节点、关系、job 节点和字段血缘统计 | `01-impact-current.body.json` |
| 2 | `impact?at=<timestamp>` 返回同一身份下的快照图 | `02-impact-snapshot.body.json` |
| 3 | `diff?from=&to=` 返回新增、移除、不变关系 | `03-lineage-diff.body.json` |
| 4 | `/sync-addax` 能回填/更新 ADDAX 血缘 | `04-sync-addax.body.json` |
| 5 | 回填后影响图仍可查询且无重复当前边 | `05-impact-after-sync.body.json` |

## 当前本地实测

2026-05-01 本地环境已完成一次 API 烟测：

```text
impact?at=2026-05-01T07:08:09Z -> nodeCount=10, edgeCount=10
diff 2020-01-01T00:00:00Z -> 2026-05-01T07:08:09Z -> addedCount=10, removedCount=0
```

## 截图要求

- LineagePage 表格页：影响概览、关系边列表、字段血缘。
- LineagePage 图页：job 节点、MiniMap、关系颜色。
- 时间旅行 Diff 页：新增/移除/不变统计。
- 导出验证：SVG 与 PNG 各导出一次。

截图放入：

```text
worklog/v2.2.3/sprint-20-202604/it/screenshots/<yyyymmdd>-<env>/
```

## 残余风险

- Inceptor 原生复杂 SQL 的列级解析仍作为 v2.4.0 增强项。
- 非 Airflow 本地 runner 的成功态回写仍依赖执行链路是否调用 platform 回调；失败/声明态已能落边。
