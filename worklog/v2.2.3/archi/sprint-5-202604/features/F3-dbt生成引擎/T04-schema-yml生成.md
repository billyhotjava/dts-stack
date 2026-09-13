# T04: schema.yml 自动生成 + 阈值测试

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标
为生成的 dbt 模型自动创建 schema.yml，包含列描述和阈值测试规则。

## 技术设计

### 生成逻辑
按 domain 聚合同域指标，生成一个 `{domain}_indicators_schema.yml`：

```java
public void generateSchemaYml(List<UUID> indicatorIds) {
    // 按 domain 分组
    Map<String, List<GovIndicatorDefinition>> byDomain = ...;
    
    for (var entry : byDomain.entrySet()) {
        StringBuilder yml = new StringBuilder();
        yml.append("version: 2\n\nmodels:\n");
        
        for (GovIndicatorDefinition def : entry.getValue()) {
            yml.append("  - name: ind_").append(def.getCode()).append("\n");
            yml.append("    description: \"").append(def.getName()).append("\"\n");
            yml.append("    columns:\n");
            
            // 指标列
            yml.append("      - name: ").append(def.getCode()).append("\n");
            yml.append("        description: \"").append(def.getName())
               .append("（").append(def.getUnit()).append("）\"\n");
            
            // 阈值测试
            if (def.getThresholdMin() != null || def.getThresholdMax() != null) {
                yml.append("        tests:\n");
                if (def.getThresholdMin() != null) {
                    yml.append("          - dbt_utils.accepted_range:\n");
                    yml.append("              min_value: ").append(def.getThresholdMin()).append("\n");
                }
                if (def.getThresholdMax() != null) {
                    yml.append("          - dbt_utils.accepted_range:\n");
                    yml.append("              max_value: ").append(def.getThresholdMax()).append("\n");
                }
            }
            
            // 维度列
            for (DimensionField dim : parseDimensions(def.getDimensionFields())) {
                yml.append("      - name: ").append(dim.field()).append("\n");
                yml.append("        description: \"").append(dim.displayName()).append("\"\n");
            }
            
            // report_period 列
            yml.append("      - name: report_period\n");
            yml.append("        description: \"报告周期\"\n");
            yml.append("        tests:\n");
            yml.append("          - not_null\n");
        }
        
        // 写入文件
        String path = "models/ads/" + entry.getKey().toLowerCase() + "/" 
                     + entry.getKey().toLowerCase() + "_indicators_schema.yml";
        dbtFileService.writeFile(path, yml.toString());
    }
}
```

### 窗口模型的 schema
如果存在 `ind_{code}_window.sql`，在同一 schema.yml 中追加其列定义（`_yoy`, `_ytd` 等）。

## 影响范围
| 文件 | 改动 |
|------|------|
| 修改 `DbtIndicatorGenerator.java` | 新增 generateSchemaYml() |

## 验证
- [ ] 生成的 yml 语法正确（dbt compile 不报错）
- [ ] 阈值测试规则正确
- [ ] 每个 domain 一个 schema 文件
