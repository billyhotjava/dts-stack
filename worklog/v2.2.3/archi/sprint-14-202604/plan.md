# Sprint-14 设计文档：Excel 导入内核统一与解析重构

**对应 Sprint**: `worklog/v2.2.3/sprint-14-202604/`

> 说明：本文是 Sprint-14 启动时的设计快照。最终实现状态、样本证据与验收结果以 `README.md` 和 `it/` 目录为准。

## 1. 背景

当前 Excel 导入链路存在三个结构性缺陷：

1. `dts-platform` 和 `dts-ingestion` 各自维护一套解析逻辑。
2. 平台侧在 parse 阶段直接产出 `data.csv`，Excel 语义过早丢失。
3. 兼容问题只能靠线上样本驱动修补，缺少统一 fixtures 与回归矩阵。

现场最新暴露的问题是非常规负数格式污染 ODS，再在 dbt cast 时变成 `NULL`。这类问题本质不是单点 bug，而是“Excel 解析内核缺位”。

## 2. 设计目标

### 2.1 本 Sprint 目标

- 以 `Apache POI` 为主实现统一 Excel 解析内核
- 建立 `CellSnapshot / RowSnapshot / ParsePolicy / ParseArtifact` 内部模型
- 平台侧 parse 先得到结构化结果，再写 `data.csv`
- ingestion 预检复用同一套 normalize 规则
- 建立最小可用 fixtures 样本库和回归矩阵

### 2.2 非目标

- 改造下游 Addax 直接读 xlsx
- 引入商业 Excel SDK
- 一次性清理历史污染数据

## 3. 核心模型

建议内部引入如下对象：

```java
record ExcelCellSnapshot(
    int rowIndex,
    int colIndex,
    String address,
    String rawType,
    String rawValue,
    String displayValue,
    String normalizedValue,
    String formatCode,
    String formula,
    boolean merged
) {}
```

```java
record ExcelRowSnapshot(
    int rowIndex,
    List<ExcelCellSnapshot> cells
) {}
```

```java
record ExcelParsePolicy(
    int headerRow,
    int dataStartRow,
    boolean fillMerged,
    boolean evaluateFormula,
    boolean normalizeLocalizedNegative,
    boolean normalizeLooseDate,
    String dateFormat
) {}
```

```java
record ExcelParseArtifact(
    List<String> headers,
    List<ExcelRowSnapshot> rows,
    List<ExcelColumnSpecDto> inferredColumns,
    Path csvPath,
    Path errorPath,
    Path manifestPath
) {}
```

## 4. 包结构建议

```
source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/excel/
  ExcelParseFacade.java
  ExcelWorkbookGateway.java
  ExcelSheetScanner.java
  ExcelCellNormalizer.java
  ExcelSchemaInferer.java
  ExcelArtifactWriter.java
  model/
    ExcelCellSnapshot.java
    ExcelRowSnapshot.java
    ExcelParsePolicy.java
    ExcelParseArtifact.java
```

`dts-ingestion` 本 Sprint 不复制这一套，只复用 normalize/policy 能力；若依赖管理方便，可后续再抽到共享模块。

## 5. Feature 分解

### F1: 统一 Excel 解析内核

- T01: 定义 Artifact / Policy / Snapshot 模型
- T02: 实现 POI 扫描器与 sheet 选择逻辑
- T03: 实现统一 normalize 规则引擎

### F2: 平台侧 ExcelImport 流水线重构

- T01: `ExcelImportService` 收口到 `ExcelParseFacade`
- T02: “先解析、后 CSV 化”的 ArtifactWriter
- T03: 保持现有 REST / 前端 / Addax 契约不变

### F3: ingestion 一致性收敛

- T01: `ExcelParseService` 复用共享 normalize 规则
- T02: 预检结果与正式导入结果对齐
- T03: 检查 ODS / Addax 侧的字段落地契约

### F4: 样本与回归

- T01: fixtures 样本库
- T02: 回归矩阵 + 历史脏数据修复建议

## 6. 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| POI 全量读取大文件内存压力 | 平台 parse 高峰期内存升高 | 本 Sprint 先保证正确性；后续如需引入 SAX/event 流式读取，只替换 `ExcelSheetScanner` 实现 |
| 平台与 ingestion 共享代码边界不清 | 重构后继续漂移 | 先统一规则与样本，再决定是否抽公共模块 |
| 现有前端隐式依赖 parse 结果细节 | 页面兼容风险 | 本 Sprint 明确保持 `/infra/excel-import/*` 主响应字段不变 |
| 历史脏数据已入 ODS | 用户以为修完自动恢复 | 文档中明确“只修新增导入链路”；补修复 SQL 方案 |

## 7. 验收门槛

- 平台侧主代码编译通过
- 平台侧 ExcelImport 样本测试通过
- ingestion 侧预检样本测试通过
- 至少 6 类 fixture 样本被纳入回归
- 形成一份历史 ODS 脏数据巡检/修复建议

## 8. 退出标准

只有同时满足以下条件，Sprint-14 才能标记 DONE：

1. 新增导入链路不再出现已知负数格式污染
2. 平台侧与 ingestion 预检对同一份样本输出一致
3. 现有前端流程无契约破坏
4. 样本库与回归矩阵可重复执行
