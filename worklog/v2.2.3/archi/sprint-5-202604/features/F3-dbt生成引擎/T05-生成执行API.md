# T05: 生成→编译→执行 API 端点

**优先级**: P0
**状态**: READY
**依赖**: T02, T04

## 目标
提供一键式 API：生成 dbt 模型 → 编译 → 执行 → 返回结果。

## 技术设计

### API 端点
```
POST /api/governance/indicators/generate
  Body: { "indicatorIds": ["uuid1", "uuid2", ...] }
  返回: GenerationResult

POST /api/governance/indicators/generate-and-run
  Body: { "indicatorIds": ["uuid1", "uuid2", ...] }
  返回: GenerationResult（含编译和执行结果）

POST /api/governance/indicators/{id}/preview-sql
  返回: { "sql": "...", "schemaYml": "..." }  预览不写文件
```

### GenerationResult
```java
public record GenerationResult(
    int totalIndicators,
    List<String> generatedFiles,
    String compileStatus,   // null if generate-only
    String runStatus,       // null if generate-only
    List<IndicatorRunSummary> runDetails,
    List<String> warnings
) {}
```

### 执行流程
```java
public GenerationResult generateAndRun(List<UUID> indicatorIds) {
    // 1. 生成 SQL + schema.yml
    List<String> files = generateBatch(indicatorIds);
    generateSchemaYml(indicatorIds);
    
    // 2. 调用现有 dbt compile
    //    复用 EtlResource 的 triggerDbtCompile 逻辑
    CompileResult compile = dbtCompileService.compile();
    
    // 3. 如果编译成功，调用 dbt run --select
    //    只运行生成的模型
    String selectPattern = files.stream()
        .map(f -> extractModelName(f))
        .collect(Collectors.joining(" "));
    RunResult run = dbtRunService.run(Map.of("select", selectPattern));
    
    // 4. 更新指标状态为 COMMITTED
    indicatorIds.forEach(id -> {
        GovIndicatorDefinition def = indicatorRepo.findById(id).orElseThrow();
        def.setStatus("COMMITTED");
        indicatorRepo.save(def);
    });
    
    return new GenerationResult(
        indicatorIds.size(), files,
        compile.status(), run.status(),
        run.details(), compile.warnings()
    );
}
```

### 预览（不写文件）
```java
public Map<String, String> previewSql(UUID indicatorId) {
    GovIndicatorDefinition def = indicatorRepo.findById(indicatorId).orElseThrow();
    String sql = renderTemplate(resolveTemplate(def.getAggregationType()), buildContext(def));
    String yml = renderSchemaForSingle(def);
    return Map.of("sql", sql, "schemaYml", yml);
}
```

## 影响范围
| 文件 | 改动 |
|------|------|
| 修改 `DbtIndicatorGenerator.java` | generateAndRun(), previewSql() |
| 修改 `GovernanceResource.java` | 新增 3 个端点 |

## 验证
- [ ] generate 生成文件但不执行
- [ ] generate-and-run 生成+编译+执行成功
- [ ] preview-sql 返回 SQL 但不写文件
- [ ] --select 只运行指定模型
