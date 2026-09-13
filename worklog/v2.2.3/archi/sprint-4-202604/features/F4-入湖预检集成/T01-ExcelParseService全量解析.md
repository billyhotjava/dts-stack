# T01: ExcelParseService — 全量解析服务

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
新增 ExcelParseService，替代现有 FileUploadService 的"只读前2行"逻辑，实现全量解析并智能推断类型。

## 技术设计

### 解析策略
```
≤5000行：XSSFWorkbook（DOM 模式，功能完整，可检测公式）
>5000行：StreamingReader（SAX 流式，内存恒定 ~50MB）
```

### 解析输出
```java
ParseResult {
    int totalRows;
    List<ColumnInfo> columns;     // name, inferredType, sampleValues
    List<FormulaCell> formulaCells; // rowNum, colName, formula
    List<Integer> emptyRows;       // 全空行号
}
```

### 类型推断改进
现有只扫前2行，改为**全量扫描统计**：
- 每列统计各类型占比（string/long/double/date/boolean）
- 占比 >80% 的类型作为推断结果
- 低于阈值标记为"类型混乱"

### 公式单元格处理
```java
if (cell.getCellType() == CellType.FORMULA) {
    // 记录公式文本，存入 formulaCells
    // 暂存表中该单元格值为 null，_errors 标记"公式单元格"
}
```

### SSE 进度推送
```
/api/ingestion/tasks/{id}/parse/progress (SSE)
→ {phase: "parsing", current: 3200, total: 8000}
```

## 影响范围
- 新增 `ExcelParseService.java` 在 dts-ingestion 模块
- 现有 `FileUploadService` 保持不变（兼容非预检场景）
- 依赖 Apache POI（已有）+ streaming-reader（需评估是否引入）

## 验证
- [ ] 100行 Excel 用 DOM 模式解析
- [ ] 10000行 Excel 用流式模式解析，内存 <100MB
- [ ] 公式单元格被正确检测和标记
- [ ] 类型推断基于全量统计而非前2行

## 完成标准
- [ ] ExcelParseService 可全量解析 .xlsx/.xls/.csv
- [ ] 返回 ParseResult 含列信息、公式、空行
