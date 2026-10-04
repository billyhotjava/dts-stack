# ELT Quality Baseline Gap Analysis

## 目标

盘点“数据接入中心 + 数据开发中心”当前测试体系的覆盖情况，识别最小稳定性门禁缺口。

## 当前已有测试

### 数据接入中心后端

- `source/dts-ingestion/src/test/java/com/yuzhi/dts/ingestion/service/IngestionTaskServiceTest.java`
- `source/dts-ingestion/src/test/java/com/yuzhi/dts/ingestion/service/IngestionTaskExecutionFilterTest.java`
- `source/dts-ingestion/src/test/java/com/yuzhi/dts/ingestion/service/IngestionTaskFullRefreshExecutionTest.java`
- `source/dts-ingestion/src/test/java/com/yuzhi/dts/ingestion/service/etl/AirflowAdapterTest.java`
- `source/dts-ingestion/src/test/java/com/yuzhi/dts/ingestion/service/etl/AirflowDagServiceTest.java`
- `source/dts-ingestion/src/test/java/com/yuzhi/dts/ingestion/service/etl/AirflowExecutionSyncServiceTest.java`
- `source/dts-ingestion/src/test/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResourceTest.java`

### 数据开发中心后端

- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/EtlResourceTest.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/IngestionTaskProxyResourceTest.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtDagServiceTest.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtOutputRelationServiceTest.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtQualityGateServiceTest.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtReleaseGateServiceTest.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtRunResultServiceTest.java`

### 前端现状

#### platform-webapp

当前已有测试主要集中在 helper 层和部分 modeling/foundation helper，不覆盖接入中心 / 开发中心页面行为。

实际检索：

```bash
rg --files source/dts-platform-webapp/src | rg "(Transform|SqlModeling|QueryWorkbench|Orchestration|ScriptStudio).*\\.(test|spec)\\.(ts|tsx)$"
```

结果为空。

已确认缺口：

- `TransformPage.tsx`
- `TransformCreatePage.tsx`
- `TransformDetailPage.tsx`
- `TransformExecutionHistoryPage.tsx`
- `QueryWorkbenchPage.tsx`
- `SqlModelingPage.tsx` 的核心交互异常场景

#### analytics-webapp

有较多 `project-cockpit` 和 screen 相关测试，但不属于本 Sprint 的 ELT 主范围。

### E2E 现状

现有 `tests/web-e2e/specs/biz/erp-ingest-visible.spec.ts` 可以覆盖部分接入相关展示，但还没有覆盖：

- 接入中心的任务创建/执行/日志/历史链
- 开发中心的 compile/test/build 发布链
- 两大中心的异常路径

## 已确认缺口

### G1：接入中心后端回归面先卡在测试编译漂移

- 不是“测试没写”，而是现有测试和实现已经失配
- `DagPreheatService` 引入后，多个单测构造器未同步
- 当前连 `surefire testCompile` 都过不了，导致真实行为回归无法开始

### G2：开发中心后端测试能运行，但关键契约已漂移

- `DbtOutputRelationServiceTest` 说明产出表重建语义变了
- `DbtQualityGateServiceTest` / `DbtReleaseGateServiceTest` 说明门禁阻断规则变了
- `EtlResourceTest` 说明资源层触发链和 DAG 可见性判断变了

### G3：前端页面行为测试几乎缺失

- 接入中心 4 个核心页面没有独立测试基线
- 开发中心关键页面没有异常路径行为测试

### G4：E2E 不覆盖 ELT 核心异常链

- 现有用例偏展示与可见性
- 缺少执行链、发布链、日志链的真实回归

### G5：人工验收未形成清单

- 目前依赖临时联调
- 不利于稳定性收口

## 当前回归结果

### 接入中心

命令：

```bash
cd source/dts-ingestion
mvn -Dtest=IngestionTaskServiceTest,IngestionTaskExecutionFilterTest,AirflowExecutionSyncServiceTest,IngestionTaskResourceTest test
```

结果：

- `BUILD FAILURE`
- 失败阶段：`testCompile`
- 直接错误：
  - `IngestionTaskExecutionFilterTest`
  - `IngestionTaskFullRefreshExecutionTest`
  - `IngestionTaskServiceTest`
    都没有传入新增的 `DagPreheatService`

结论：

- 接入中心后端最小门禁当前为红
- 首批修复必须先把测试回归面恢复到可运行状态

### 开发中心

命令：

```bash
cd source/dts-platform
mvn -Dtest=DbtDagServiceTest,DbtOutputRelationServiceTest,EtlResourceTest,DbtQualityGateServiceTest,DbtReleaseGateServiceTest test
```

结果：

- `Tests run: 20, Failures: 6, Errors: 5`
- 关键失败类型：
  - 门禁语义断言失败
  - 产出表重建断言失败
  - 资源层等待 DAG 可见性超时 / 502

结论：

- 开发中心后端最小门禁也为红
- 失败不是单点 bug，而是“实现语义变化 + 资源层等待策略变化 + 测试未同步”

## 建议的最小门禁

### 后端

#### 数据接入中心

```bash
cd source/dts-ingestion
mvn -Dtest=IngestionTaskServiceTest,IngestionTaskExecutionFilterTest,AirflowExecutionSyncServiceTest,IngestionTaskResourceTest test
```

说明：

- 当前这组命令先卡在 `testCompile`
- 首批修复的第一步就是让它重新可运行

#### 数据开发中心

```bash
cd source/dts-platform
mvn -Dtest=DbtDagServiceTest,DbtOutputRelationServiceTest,EtlResourceTest,DbtQualityGateServiceTest,DbtReleaseGateServiceTest test
```

说明：

- 当前这组命令可运行，但结果为红
- 它应作为开发中心首批修复的主门禁

### 前端

```bash
cd source/dts-platform-webapp
pnpm build
```

### E2E

建议新增至少两组：

- 接入中心冒烟
- 开发中心冒烟

执行方式：

```bash
cd tests/web-e2e
pnpm test -- --project=chromium
```

## 结论

当前 ELT 稳定性问题的根本不是“没有测试”，而是：

- 已有后端测试与当前实现契约发生漂移
- 页面行为测试缺口大
- E2E 对异常链路覆盖不足
- 没有形成“先恢复回归面、再修真实问题”的执行顺序
