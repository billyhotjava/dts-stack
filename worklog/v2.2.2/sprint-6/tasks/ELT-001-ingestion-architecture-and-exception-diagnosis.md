# ELT-001：接入中心架构与异常链路诊断

## 目标

明确接入中心从页面到 Airflow 的完整链路，并定位异常路径上的薄弱环节。

## 重点文件

- `source/dts-platform-webapp/src/pages/explore/etl/TransformPage.tsx`
- `source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`
- `source/dts-platform-webapp/src/pages/explore/etl/TransformDetailPage.tsx`
- `source/dts-platform-webapp/src/pages/explore/etl/TransformExecutionHistoryPage.tsx`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskService.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/DagPreheatService.java`

## 交付标准

- 画清主链和异常链
- 标清高风险依赖点
- 沉淀到 `req/elt-architecture-and-dependency-map.md`

## 当前结论

- 已确认接入中心主链是：
  - `Transform* 页面 -> ingestion.ts -> IngestionTaskResource -> IngestionTaskService -> AirflowAdapter/AirflowClient -> AirflowExecutionSyncService`
- 已确认异步执行入口会吞后台异常：
  - `IngestionTaskService.executeAsync()`
  - `IngestionTaskService.retryExecutionAsync()`
- 已确认前端依赖 `latest execution` 轮询来感知真正执行结果，因此后端状态写入一旦延迟，页面就会失真

## 已执行验证

```bash
rg -n "Transform(Create|Detail|ExecutionHistory|Page)|IngestionTask(Resource|Service)|DagPreheatService|AirflowAdapter|AirflowExecutionSyncService" \
  source/dts-platform-webapp/src source/dts-ingestion/src

cd source/dts-ingestion
mvn -Dtest=IngestionTaskServiceTest,IngestionTaskExecutionFilterTest,AirflowExecutionSyncServiceTest,IngestionTaskResourceTest test
```

## 当前阻断

- 接入中心最小回归面当前卡在 `testCompile`
- 直接原因是 `DagPreheatService` 引入后，多个测试类未同步更新构造参数
