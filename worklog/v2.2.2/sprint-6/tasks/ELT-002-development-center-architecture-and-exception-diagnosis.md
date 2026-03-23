# ELT-002：开发中心架构与异常链路诊断

## 目标

明确开发中心从页面到 dbt / Airflow 的完整链路，并定位异常路径上的薄弱环节。

## 重点文件

- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- `source/dts-platform-webapp/src/pages/explore/QueryWorkbenchPage.tsx`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtDagService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtOutputRelationService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/RollbackCascadeService.java`

## 交付标准

- 画清主链和异常链
- 标清与 Airflow/dbt/门禁/回滚的关键耦合点
- 沉淀到 `req/elt-architecture-and-dependency-map.md`

## 当前结论

- 已确认开发中心主链是：
  - `SqlModelingPage -> EtlResource -> DbtDagService/AirflowClient -> Airflow DAG -> dbt -> DbtRunResultService`
- 已确认 `waitForDagRegistration()` 仍会把真实 Airflow 错误误报成 “DAG 尚未注册”
- 已确认门禁、产出表重建、回滚后二次触发等语义与测试断言已漂移

## 已执行验证

```bash
rg -n "SqlModelingPage|QueryWorkbenchPage|OrchestrationPage|ScriptStudioPage|EtlResource|DbtDagService|DbtRunResultService|RollbackCascadeService" \
  source/dts-platform-webapp/src source/dts-platform/src

cd source/dts-platform
mvn -Dtest=DbtDagServiceTest,DbtOutputRelationServiceTest,EtlResourceTest,DbtQualityGateServiceTest,DbtReleaseGateServiceTest test
```

## 当前阻断

- `DbtOutputRelationServiceTest`、`DbtQualityGateServiceTest`、`DbtReleaseGateServiceTest` 当前为 fail
- `EtlResourceTest` 当前以 502 和 15s timeout 的形式暴露资源层等待策略问题
