# P0-03 dbt 运行最小参数化

- 优先级：P0
- 状态：done

## 范围

- 将 dbt 运行从固定 `models=all,target=dev` 升级为最小可配置。

## 子任务

- `DbtFileBrowserPage.tsx` 增加运行参数弹窗：selector、target、operation(run/test)。
- `EtlResource.java` 扩展请求结构与参数校验。
- `DbtDagService.java` 透传 selector/target，并记录审计字段。
- 运行记录页展示本次 selector/target。

## 验收标准

- 用户可在 UI 指定 selector 与 target。
- 触发后 Airflow conf 可见传入参数。
- run/test 至少两类命令可执行。

## 风险与回滚

- 风险：参数放开导致误跑全量。
- 回滚：默认值仍为安全配置，且新增确认提示。

## 已完成进展（2026-02-16）

- 前端已改为参数化触发：
  - `source/dts-platform-webapp/src/pages/modeling/DbtFileBrowserPage.tsx`
  - 新增弹窗输入 `operation(run/test)`、`models(selector)`、`target`
- 后端已增加 operation 校验并写入 conf：
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
  - operation 非 `run/test` 返回 400
- DAG 模板已支持按 conf 选择 run/test：
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtDagService.java`

## 待完成

- 无

## 回归结果（2026-02-16）

- `pnpm -C source/dts-platform-webapp build`：通过
- `mvn -f source/dts-platform/pom.xml -DskipTests compile`：通过
