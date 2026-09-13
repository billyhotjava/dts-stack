# T02: QualityRunService 填充 rows_total + metric_value

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
在规则执行时记录目标表总行数，在 persistMetrics 时填充 metric_value。

## 技术设计

### rows_total 填充
在 `doExecuteRun()` 方法中，执行检测 SQL 之前，先查询目标表行数：

```java
// 解析目标表名（复用 resolveTableName）
String tableName = resolveTableName(run);
if (tableName != null) {
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(
             "SELECT count(*) FROM " + tableName)) {
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            run.setRowsTotal(rs.getInt(1));
        }
    }
}
```

### metric_value 填充
在 `persistMetrics()` 中，从 `StatementExecutionResult` 提取数值型结果：

```java
// 现有代码只设置了 key/status/detail
// 补充：
metric.setMetricValue(extractMetricValue(result));
metric.setThresholdValue(extractThresholdFromRule(rule));
```

`extractMetricValue`: 从 result detail 或 failingRowCount 计算通过率作为 metric_value。

### failingRowCount 一致性
确保 `failingRowCount` 和 `rowsTotal` 满足约束：`failingRowCount <= rowsTotal`。

## 影响范围
| 文件 | 改动 |
|------|------|
| 修改 `QualityRunService.java` | doExecuteRun + persistMetrics |

## 验证
- [ ] 新执行的 run 记录 rows_total > 0
- [ ] metric_value 正确反映通过率
- [ ] threshold_value 从规则中提取
