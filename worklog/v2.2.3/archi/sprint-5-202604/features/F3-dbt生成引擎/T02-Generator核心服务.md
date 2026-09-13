# T02: DbtIndicatorGenerator 核心服务

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
实现指标定义 → dbt SQL 文件的核心生成逻辑。

## 技术设计

### DbtIndicatorGenerator
```java
@Service
public class DbtIndicatorGenerator {
    private final GovIndicatorDefinitionRepository indicatorRepo;
    private final DbtFileService dbtFileService;  // 复用现有文件写入

    /**
     * 根据指标定义生成 dbt SQL 文件
     * @return 生成的文件路径
     */
    public String generate(UUID indicatorId) {
        GovIndicatorDefinition def = indicatorRepo.findById(indicatorId).orElseThrow();
        
        // 1. 根据 aggregationType 选择模板
        String template = resolveTemplate(def.getAggregationType());
        
        // 2. 构建模板上下文
        Map<String, Object> context = buildContext(def);
        
        // 3. 渲染 SQL
        String sql = renderTemplate(template, context);
        
        // 4. 确定输出路径：models/ads/{domain}/ind_{code}.sql
        String path = buildOutputPath(def);
        
        // 5. 写入文件
        dbtFileService.writeFile(path, sql);
        
        // 6. 如果有 window_function != NONE，追加窗口模型
        if (!"NONE".equals(def.getWindowFunction())) {
            String windowSql = renderWindowTemplate(def);
            String windowPath = path.replace(".sql", "_window.sql");
            dbtFileService.writeFile(windowPath, windowSql);
        }
        
        // 7. 更新 targetModelName
        def.setTargetModelName(extractModelName(path));
        indicatorRepo.save(def);
        
        return path;
    }

    /**
     * 批量生成（模板展开后的一组指标）
     */
    public List<String> generateBatch(List<UUID> indicatorIds) {
        // 拓扑排序（衍生指标在依赖之后）
        List<UUID> sorted = topologicalSort(indicatorIds);
        return sorted.stream().map(this::generate).toList();
    }

    /**
     * 生成 + 编译 + 执行
     */
    public GenerationResult generateAndRun(List<UUID> indicatorIds) {
        List<String> paths = generateBatch(indicatorIds);
        generateSchemaYml(indicatorIds);  // T04 实现
        // 调用现有 dbt compile + run
        // 返回结果
    }
    
    // --- 内部方法 ---
    
    private String resolveTemplate(String aggregationType) {
        return switch (aggregationType) {
            case "RATIO" -> "ratio.sql.j2";
            case "CUSTOM" -> "custom.sql.j2";
            default -> "simple_aggregate.sql.j2";  // SUM/COUNT/AVG/MAX/MIN
        };
    }
    
    private Map<String, Object> buildContext(GovIndicatorDefinition def) {
        // 解析 JSON 字段，构建模板变量 Map
        // dimension_fields, joins, filters 等
    }
    
    private String renderTemplate(String templateName, Map<String, Object> context) {
        // 读取模板文件，用 String.replace 或简单模板引擎替换变量
        // 注意：Java 侧不使用 Jinja2，而是简单的 {{var}} 替换
        // 复杂逻辑（if/for）在 Java 中处理，模板保持纯 SQL + 占位符
    }
    
    private String buildOutputPath(GovIndicatorDefinition def) {
        String domain = (def.getDomain() != null ? def.getDomain() : "general").toLowerCase();
        return "models/ads/" + domain + "/ind_" + def.getCode() + ".sql";
    }
}
```

### 模板渲染策略
Java 侧不引入 Jinja2 依赖。模板的 `{% if %}` / `{% for %}` 逻辑在 Java 的 `buildContext` + `renderTemplate` 中处理：
- Java 负责条件判断、循环展开
- 模板只保留 `{{ var }}` 占位符
- 与 Sprint-4 的 `SqlTemplateRenderer` 风格一致

## 影响范围
| 文件 | 改动 |
|------|------|
| 新增 `DbtIndicatorGenerator.java` | 核心服务 |
| 新增 `GenerationResult.java` | 结果 DTO |

## 验证
- [ ] 单指标生成文件正确
- [ ] 批量生成文件正确
- [ ] 输出路径结构 models/ads/{domain}/ind_{code}.sql
