# T02: DAG Python 模板加 inlets/outlets

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

把 `AirflowDagService` 生成的 DAG Python 代码升级为 lineage-aware：每个 Operator 显式声明 `inlets`/`outlets`，必要时用 PythonOperator wrapper 主动发 OpenLineage 事件。

## 技术设计

### DAG 模板修改

`dts-ingestion/.../service/etl/AirflowDagService.java`：

`buildDagSource()` (L372-514) 与 `buildMultiTaskDagSource()` (L520-607)、`buildApiDagSource()` (L926-1112) 的 Python 字符串模板里：

1. 在 import 段加：

```python
from airflow.lineage.entities import Table
from openlineage.airflow import DAG  # 如果用 OpenLineage 的 DAG 替换
```

2. 把每个 DockerOperator 改为：

```python
DockerOperator(
    task_id="addax_run",
    image=...,
    inlets=[Table(database="$source_db", cluster="$source_ds", name="$source_table")],
    outlets=[Table(database="$target_db", cluster="$target_ds", name="$target_table")],
    ...
)
```

3. PythonOperator/API DAG 增加显式 emit：

```python
from openlineage.client import OpenLineageClient
from openlineage.client.run import RunEvent, RunState, Run, Job
def emit_complete(**ctx):
    client = OpenLineageClient.from_environment()
    client.emit(RunEvent(...))
```

### Java 端改动

新增方法 `renderInletsOutletsBlock(IngestionTask task)`：根据 `tableMapping` 渲染 Python `[Table(...), ...]` 列表字符串。注意 Python 字符串转义。

### 输出 DAG diff 范例

`worklog/v2.2.3/sprint-20-202604/assets/dag-template-before-after.md`，前后对比示例。

### 配置

```yaml
dts:
  airflow:
    openlineage:
      enabled: ${DTS_AIRFLOW_OPENLINEAGE_ENABLED:true}
      transport-url: ${DTS_OPENLINEAGE_URL:http://dts-platform:8080/api/internal/lineage/openlineage}
```

DAG 模板在 enabled=false 时不输出 inlets/outlets，保持兼容回退。

### 旧 DAG 处理

- 现网已存在的 DAG 文件不会被修改；
- 提供"重新生成所有 DAG"的批量任务（Airflow 端 admin endpoint 或 dts-ingestion CLI），文档化在 it/runbook。

## 影响范围

- `dts-ingestion/.../service/etl/AirflowDagService.java` —— 三个 build* 方法 + 新增 helper
- `dts-ingestion/src/test/java/.../AirflowDagServiceTest.java` —— 新增 lineage 渲染测试
- `application.yml`
- `worklog/.../assets/dag-template-before-after.md`

## 验证

- [ ] `AirflowDagServiceTest` 新增用例：jdbc 单表、jdbc 多表、API 任务，验证生成的 Python 含 `inlets=[Table(...)]`
- [ ] 单测：tableMapping 为空时不报错，生成的 DAG 不含 inlets/outlets
- [ ] 单测：Python 字符串特殊字符（schema 含 `_`、`.`）被正确转义
- [ ] 把生成的 DAG 落到本地 Airflow，能解析、能跑（本地 docker-compose 验证）
- [ ] OpenLineage 事件 payload `inputs[]`/`outputs[]` 字段非空

## 完成标准

- [ ] 三个 build* 方法都已支持渲染 inlets/outlets
- [ ] 单测+集成测试全绿
- [ ] 配置开关可关闭
- [ ] 文档前后对比已写
