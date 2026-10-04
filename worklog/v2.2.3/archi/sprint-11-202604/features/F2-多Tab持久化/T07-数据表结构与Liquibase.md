# T07: sql_ide_tab 表结构与 Liquibase

**优先级**: P0
**状态**: READY
**依赖**: F1

## 目标

新增 `sql_ide_tab` 表用于存储用户级 Tab 状态，Liquibase changelog 纳入版本管理。

## 技术设计

### 表结构

```sql
CREATE TABLE sql_ide_tab (
    id                UUID PRIMARY KEY,
    user_id           VARCHAR(255) NOT NULL,
    title             VARCHAR(200),
    sql_text          TEXT,
    engine            VARCHAR(50),
    datasource_id     UUID,
    schema_ctx        VARCHAR(500),           -- catalog.schema 上下文
    cursor_line       INT,
    cursor_col        INT,
    selection         JSONB,                  -- {startLine,startCol,endLine,endCol}
    last_execution_id UUID,                   -- 指向 query_execution.id
    sort_order        INT NOT NULL DEFAULT 0, -- Tab 顺序
    active            BOOLEAN DEFAULT FALSE,  -- 上次关闭时激活的 Tab
    created_at        TIMESTAMP NOT NULL,
    updated_at        TIMESTAMP NOT NULL
);

CREATE INDEX idx_sql_ide_tab_user ON sql_ide_tab(user_id);
CREATE INDEX idx_sql_ide_tab_updated ON sql_ide_tab(user_id, updated_at DESC);
```

### 约束

- `user_id` 从 JWT token 的 `preferred_username` 或 `sub` 取值，与现有审计一致
- `last_execution_id` 不加外键约束（结果过期会清理）
- `sql_text` 不限长（TEXT），但服务端限制 1MB

### Liquibase changelog

- 文件：`src/main/resources/config/liquibase/changelog/{YYYYMMDDHHmm}_sql_ide_tab.xml`
- 按现有项目约定，加入 `master.xml` include

### JPA 实体

```java
@Entity
@Table(name = "sql_ide_tab")
public class SqlIdeTab {
    @Id private UUID id;
    private String userId;
    private String title;
    @Lob private String sqlText;
    private String engine;
    private UUID datasourceId;
    private String schemaCtx;
    private Integer cursorLine;
    private Integer cursorCol;
    @Type(JsonBinaryType.class) private Map<String, Integer> selection;
    private UUID lastExecutionId;
    private int sortOrder;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    // ... getter/setter
}
```

## 影响范围

- 新增 Liquibase changelog 文件
- 新增 `domain/sql/SqlIdeTab.java` 实体
- 新增 `repository/SqlIdeTabRepository.java` JPA 仓库

## 验证

- [ ] 启动应用 Liquibase 自动建表
- [ ] `@DataJpaTest` 集成测试可 save/find
- [ ] 索引存在（`\d sql_ide_tab` 可见）

## 完成标准

- [ ] 表结构与 changelog 提交
- [ ] JPA 实体与仓库可用
- [ ] 集成测试绿
