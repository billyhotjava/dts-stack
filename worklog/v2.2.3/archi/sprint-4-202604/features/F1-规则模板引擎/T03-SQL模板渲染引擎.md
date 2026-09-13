# T03: SQL 模板渲染引擎

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
实现参数化 SQL 模板渲染，支持参数校验、表名/列名白名单、防 SQL 注入。

## 技术设计

### 核心类 `SqlTemplateRenderer`

```java
public class SqlTemplateRenderer {
    /**
     * 渲染模板 SQL
     * @param sqlTemplate 模板字符串，如 "SELECT * FROM {{table}} WHERE {{column}} IS NULL"
     * @param params      用户填写的参数 Map
     * @param paramSchema 模板定义的参数 schema（用于校验）
     * @param metadata    数据源元数据（可用表名/列名白名单）
     * @return 渲染后的可执行 SQL
     */
    public String render(String sqlTemplate, Map<String,Object> params,
                         List<ParamDef> paramSchema, DataSourceMetadata metadata);
}
```

### 安全机制

1. **参数类型校验**：根据 param_schema 的 type 校验
   - `table_select` → 必须存在于数据源元数据的表列表中
   - `column_select` → 必须存在于指定表的列列表中
   - `text_array` → 每个值用单引号转义
   - `number` → 必须是数字
   - `pattern` → 校验为合法正则
2. **白名单**：table/column 参数只接受已知标识符（`[a-zA-Z0-9_]+`）
3. **值转义**：字符串值用 `''` 转义，防止 SQL 注入
4. **渲染后 SQL 审计**：渲染结果写入日志

### 模板占位符语法
- `{{param_name}}` — 简单替换
- `{{param_name:quote}}` — 加引号
- `{{param_name:csv}}` — 数组展开为逗号分隔（带引号）

## 影响范围
| 文件 | 改动 |
|------|------|
| 新增 `SqlTemplateRenderer.java` | 渲染引擎 |
| 新增 `SqlTemplateRendererTest.java` | 单元测试 |

## 验证
- [ ] 10 种模板渲染结果 SQL 语法正确
- [ ] 非法表名/列名被拒绝
- [ ] SQL 注入尝试被阻断
- [ ] 单元测试覆盖

## 完成标准
- [ ] 渲染引擎实现
- [ ] 安全机制通过测试
