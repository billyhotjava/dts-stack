# P1-02 脚本开发 MVP 落地

- 优先级：P1
- 状态：done

## 范围

- 将脚本开发从静态占位页升级为最小可用产品。

## 子任务

- 后端新增脚本资产实体与 API：脚本 CRUD、版本、运行。
- 对接执行器（先接 Airflow DockerOperator/现有脚本容器）。
- 前端 `ScriptStudioPage.tsx` 改为真实数据与日志展示。
- 增加脚本运行审计和失败分类。

## 验收标准

- 可新建脚本、保存版本、手动运行、查看日志。
- 页面不再使用空数组常量作为数据源。
- 运行记录可追溯到 executionId。

## 风险与回滚

- 风险：执行环境隔离不完善。
- 回滚：先只开放只读展示与导入，不开放在线执行。

## 已完成进展（2026-02-16）

- 后端新增脚本开发 MVP 数据模型与持久化：
  - `dev_script_asset`（脚本资产）
  - `dev_script_version`（版本）
  - `dev_script_run`（运行记录）
  - 对应 Liquibase：`source/dts-platform/src/main/resources/config/liquibase/changelog/20260216_01_dev_script_studio.xml`
- 后端新增脚本开发 API：
  - `GET /api/development/scripts`
  - `POST /api/development/scripts`
  - `PUT /api/development/scripts/{scriptId}`
  - `GET /api/development/scripts/{scriptId}/versions`
  - `POST /api/development/scripts/{scriptId}/versions`
  - `GET /api/development/scripts/{scriptId}/runs`
  - `POST /api/development/scripts/{scriptId}/run`
  - `GET /api/development/scripts/runs/{runId}`
- 运行执行器采用 MVP 方案：
  - 异步执行 + executionId
  - 按 `VALIDATION_ERROR / RUNTIME_ERROR / SYSTEM_ERROR` 分类失败类型
  - 持久化日志、耗时、状态，可用于页面追踪
- 前端 `ScriptStudioPage.tsx` 已改为真实数据驱动：
  - 脚本列表、版本加载、版本保存、运行触发
  - 运行历史列表显示 executionId / 状态 / 耗时
  - 日志弹窗查看单次运行明细

## 回归结果（2026-02-16）

- `mvn -f source/dts-platform/pom.xml -DskipTests compile`：通过
- `pnpm -C source/dts-platform-webapp build`：通过
