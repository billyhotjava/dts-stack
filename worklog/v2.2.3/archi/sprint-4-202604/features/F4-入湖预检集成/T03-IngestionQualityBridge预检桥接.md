# T03: IngestionQualityBridge — 预检桥接服务

**优先级**: P0
**状态**: READY
**依赖**: T02, F3

## 目标
桥接 Ingestion 和 Quality 两个模块，实现"暂存表数据跑质量规则"的预检流程。

## 技术设计

### 核心方法
```java
IngestionQualityBridge {
    PreCheckResult preCheck(UUID taskId, UUID datasetId);
    PreCheckResult reCheck(UUID taskId, UUID datasetId);
    void submitToLake(UUID taskId);
}
```

### preCheck 流程
1. 查询 datasetId 绑定的所有 PUBLISHED 规则（GovRuleBinding）
2. 对每条规则，用 SqlTemplateRenderer 渲染 SQL
   - **关键**：将 table 参数替换为 `tmp_ingestion_{taskId}`
3. 执行器使用 PG（非 Hive）— 暂存表在 PG 中
4. 收集失败行，回写到暂存表 `_errors` 字段
5. 返回汇总结果

### PreCheckResult
```java
PreCheckResult {
    int totalRows;
    int passedRows;
    int failedRows;
    List<RuleResult> errorsByRule;  // ruleName, ruleType, failCount
    List<RowError> sampleErrors;    // 前100条明细
}
```

### submitToLake 流程
1. 验证暂存表中 `_status` 全部为 CLEAN/FIXED
2. 生成 Addax 作业配置（数据源从暂存表读取，非原始 Excel）
3. 触发 Airflow DAG
4. 成功后 DROP 暂存表

### 执行器切换
现有 `QualityRunService` 使用 `HiveStatementExecutor`。需要增加执行器抽象：
```java
interface QualityStatementExecutor {
    List<Map<String, Object>> execute(String sql, int maxRows);
}
// 实现：HiveStatementExecutor, PgStatementExecutor
```
预检场景注入 PgStatementExecutor。

## 影响范围
- 新增 `IngestionQualityBridge.java` 在 dts-platform 模块
- 新增 `PgStatementExecutor.java`
- `QualityRunService`：抽象执行器接口
- `AddaxJobService`：支持从暂存表读取数据源

## 验证
- [ ] 预检正确检出暂存表中的不合规数据
- [ ] 错误信息精确到行号 + 列名 + 规则名 + 错误原因
- [ ] reCheck 编辑后重新检查结果正确
- [ ] submitToLake 暂存表数据成功写入目标表

## 完成标准
- [ ] 预检 → 编辑 → 重检 → 提交入湖 全链路跑通
- [ ] 执行器接口抽象完成（PG/Hive 可切换）
