# T01: SPI 接口定义

**优先级**: P2
**状态**: READY
**依赖**: F3/T02

## 目标
定义 LLM 辅助指标建模的 SPI 接口。

## 技术设计

### 接口
```java
package com.yuzhi.dts.platform.service.governance.spi;

/**
 * LLM 辅助指标建模提供者接口。
 * v2.2.x: NoOp 实现
 * v2.3.0: 接入 LLM 实现
 */
public interface IndicatorSuggestionProvider {

    /**
     * 扩展点 1: 根据源表结构推荐指标定义
     * 用于：实施人员绑定源表时，LLM 分析表结构推荐指标
     */
    List<IndicatorSuggestion> suggestFromSchema(TableSchemaInfo schema);

    /**
     * 扩展点 2: 审查生成的 SQL，返回改进建议
     * 用于：预览 SQL 时，LLM 审查逻辑合理性
     */
    SqlReviewResult reviewSql(String sql, GovIndicatorDefinition definition);

    /**
     * 扩展点 3: 分析上传文件，推荐指标模板
     * 用于：客户上传 Excel 后，LLM 推荐适用的指标模板
     */
    List<TemplateSuggestion> suggestFromUpload(UploadedFileInfo file);
}
```

### DTO
```java
public record IndicatorSuggestion(
    String code, String name, String aggregationType,
    String measureField, String expression,
    double confidence, String reasoning
) {}

public record SqlReviewResult(
    boolean approved, List<String> suggestions, List<String> warnings
) {}

public record TemplateSuggestion(
    String templateCode, double confidence, String reasoning,
    Map<String, String> suggestedFieldMapping
) {}

public record TableSchemaInfo(
    String tableName, List<ColumnInfo> columns
) {}

public record UploadedFileInfo(
    String fileName, String fileType, List<String> headers,
    List<List<String>> sampleRows
) {}
```

## 影响范围
| 文件 | 改动 |
|------|------|
| 新增 `spi/IndicatorSuggestionProvider.java` | 接口 |
| 新增 DTO records | 5 个 DTO |

## 验证
- [ ] 接口编译通过
- [ ] DTO 可序列化
