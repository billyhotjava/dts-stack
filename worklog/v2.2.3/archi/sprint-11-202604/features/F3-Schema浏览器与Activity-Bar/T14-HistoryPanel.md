# T14: HistoryPanel（复用 query_execution）

**优先级**: P1
**状态**: READY
**依赖**: T12

## 目标

实现执行历史面板，展示当前用户的查询历史（时间倒序），支持过滤与一键重开 Tab。

## 技术设计

### 数据源

- 复用现有 `query_execution` 表，按 `user_id = currentUser` 过滤
- 新增端点 `GET /api/sql/v2/history?status=&datasourceId=&from=&to=&q=&page=&size=`

### 列表项展示

```
✓ 2026-04-12 10:32  0.42s  1,234 rows  [Trino · production]
SELECT u.name, COUNT(o.id) FROM users u JOIN orders o ON...
```

- 状态图标：✓ 成功 / ✗ 失败 / ⊘ 取消 / ⏳ 运行中
- SQL 片段前 60 字符 + 省略号
- 点击 → 右侧浮层显示完整 SQL + 结果摘要
- 双击 → 新开 Tab 填入 SQL

### 过滤器

- 状态：全部 / 成功 / 失败 / 取消
- 数据源：下拉
- 时间范围：Ant Design DateRangePicker
- 关键词：匹配 `sql_text` ILIKE

### 分页

- 默认 50 条/页，滚动到底自动加载下一页

### 隐私

- 只看当前用户自己的历史
- 三员体系内的超级管理员也不破例（由 AuthenticationFacade 保证）

## 影响范围

- 新增 `history/HistoryPanel.tsx`
- `SqlIdeResource` 新增 `GET /api/sql/v2/history`
- 可能需要 `query_execution` 表新增索引 `(user_id, created_at DESC)`

## 验证

- [ ] 历史列表按时间倒序
- [ ] 过滤器生效
- [ ] 双击新开 Tab
- [ ] 只看到自己的历史
- [ ] 分页流畅

## 完成标准

- [ ] 面板功能完整
- [ ] 后端接口通过集成测试
