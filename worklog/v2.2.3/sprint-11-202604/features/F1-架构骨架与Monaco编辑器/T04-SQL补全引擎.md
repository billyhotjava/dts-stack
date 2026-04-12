# T04: SQL 补全引擎（catalog + keyword + history）

**优先级**: P0
**状态**: READY
**依赖**: T03

## 目标

实现基于 catalog 元数据的智能补全，替代 Monaco 默认的纯关键字补全，提升 SQL 编写效率。

## 技术设计

### 补全源优先级

| 来源 | 触发 | 数据 |
|---|---|---|
| **Catalog** | `.` / `FROM` / `JOIN` 后 | 从 `useCatalog` hook 拿 schema/table/column |
| **Keyword** | 全局 | 通用 ANSI SQL 保留字列表 |
| **History** | 全局低优先级 | 当前用户最近 100 条执行里出现过的表名/列名 |
| **Alias** | 编辑器本地解析 | 解析当前 SQL 的 `AS alias`，让 `alias.` 能补列 |

### 实现入口

```typescript
monaco.languages.registerCompletionItemProvider('sql', {
  triggerCharacters: ['.', ' '],
  async provideCompletionItems(model, position) {
    const beforeCursor = model.getValueInRange({...});
    const ctx = parseContext(beforeCursor);
    switch (ctx.kind) {
      case 'afterDot':   return { suggestions: await columnsOf(ctx.table) };
      case 'afterFrom':  return { suggestions: await allTables() };
      case 'default':    return { suggestions: keywordsAndRecent() };
    }
  },
});
```

### 轻量 SQL 上下文解析

- 不引入完整 SQL parser（开发量不划算）
- 正则识别 `FROM xxx` / `JOIN xxx` / `xxx AS alias` / `alias.` 场景即可
- 文件：`editor/completion/contextParser.ts`

### 元数据缓存

- React Query 按 `(datasourceId, schema, table)` 缓存列清单，`staleTime = 5min`
- 列信息包含：`name`, `dataType`, `nullable`, `comment`，Monaco `CompletionItem.documentation` 渲染类型和注释

## 影响范围

- 新增 `completion/catalogProvider.ts`
- 新增 `completion/keywordProvider.ts`
- 新增 `completion/contextParser.ts`
- 依赖 T10/T11（Schema 接口），在 F3 未完成前用 mock 数据开发

## 验证

- [ ] 输入 `SELECT * FROM ` 弹出表列表
- [ ] 输入 `table.` 弹出该表的列列表，带类型/注释
- [ ] 输入 `u` 联想到 `users` 表和关键字 `UPDATE` / `USE`
- [ ] 使用别名 `FROM users u JOIN orders o ON u.` 时，可补 `users` 的列
- [ ] 补全响应 ≤200ms（命中缓存时 ≤50ms）

## 完成标准

- [ ] 四类补全源全部生效
- [ ] 别名识别至少覆盖 `FROM x AS y` 和 `FROM x y` 两种写法
- [ ] 单元测试覆盖 contextParser
