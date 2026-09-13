# T02: NoOp 实现 + 集成点

**优先级**: P2
**状态**: READY
**依赖**: T01

## 目标
提供空实现，确保现有流程不受影响；在关键位置预埋调用点。

## 技术设计

### NoOp 实现
```java
@Service
@ConditionalOnMissingBean(IndicatorSuggestionProvider.class)
public class NoOpIndicatorSuggestionProvider implements IndicatorSuggestionProvider {
    
    @Override
    public List<IndicatorSuggestion> suggestFromSchema(TableSchemaInfo schema) {
        return List.of();  // 不推荐
    }

    @Override
    public SqlReviewResult reviewSql(String sql, GovIndicatorDefinition definition) {
        return new SqlReviewResult(true, List.of(), List.of());  // 直接通过
    }

    @Override
    public List<TemplateSuggestion> suggestFromUpload(UploadedFileInfo file) {
        return List.of();  // 不推荐
    }
}
```

### 集成点预埋

1. **DbtIndicatorGenerator.generate()** 中：
```java
// 生成 SQL 后，可选 LLM 审查
SqlReviewResult review = suggestionProvider.reviewSql(sql, def);
if (!review.approved()) {
    log.info("LLM review suggestions for {}: {}", def.getCode(), review.suggestions());
    // 当前不阻断，只记录日志
}
```

2. **IndicatorTemplateService.applyTemplate()** 中：
```java
// 绑定源表时，可选 LLM 推荐
List<IndicatorSuggestion> suggestions = suggestionProvider.suggestFromSchema(schema);
// 返回给前端展示（如果非空）
```

3. **GovernanceResource 文件上传处理** 中：
```java
// 上传 Excel 后，可选 LLM 推荐模板
List<TemplateSuggestion> suggestions = suggestionProvider.suggestFromUpload(fileInfo);
```

### Spring 条件注入
v2.3.0 只需实现 `IndicatorSuggestionProvider` 接口并标注 `@Service`，NoOp 自动让位（`@ConditionalOnMissingBean`）。

## 影响范围
| 文件 | 改动 |
|------|------|
| 新增 `NoOpIndicatorSuggestionProvider.java` | 空实现 |
| 修改 `DbtIndicatorGenerator.java` | 预埋调用 |
| 修改 `IndicatorTemplateService.java` | 预埋调用 |

## 验证
- [ ] NoOp 注入成功，不影响现有流程
- [ ] 调用点不抛异常
- [ ] 日志记录正确
