# T15: SavedPanel（saved_query + folder 分组）

**优先级**: P1
**状态**: READY
**依赖**: T12

## 目标

实现已保存查询面板，支持文件夹虚拟分组、重命名、删除、跨用户分享（受三员权限约束）。

## 技术设计

### 数据表变更

- `saved_query` 表新增字段 `folder VARCHAR(200)`（可选，null 即"无分组"）
- Liquibase changelog 新增

### API

| 方法 | 路径 | 作用 |
|---|---|---|
| `GET` | `/api/sql/v2/saved` | 列出 |
| `POST` | `/api/sql/v2/saved` | 新建（含 folder） |
| `PATCH` | `/api/sql/v2/saved/{id}` | 改标题/SQL/folder |
| `DELETE` | `/api/sql/v2/saved/{id}` | 删除 |
| `POST` | `/api/sql/v2/saved/{id}/share` | 分享给部门内其他用户（可选项，P1 可推迟） |

复用现有 `SavedQueryService`，补足 folder 字段。

### UI 交互

- 树形结构，folder 为节点、query 为叶
- 单击 query → 新开 Tab 填入 SQL
- 右键 folder：重命名 / 删除
- 右键 query：重命名 / 删除 / 移动到其他 folder / 分享
- 拖拽 query 到其他 folder 改变分组

### Ctrl+S 保存入口

- 编辑器内 Ctrl+S 弹"保存为 Saved Query"对话框
- 字段：title（必填）、folder（选或新建）、SQL 自动填充

### 审计

- `SAVED_QUERY_SAVE` / `SAVED_QUERY_LOAD` / `SAVED_QUERY_DELETE` / `SAVED_QUERY_SHARE`

## 影响范围

- `saved_query` 表新增列
- `SavedQueryService` 补 folder 支持
- 新增 `history/SavedPanel.tsx`

## 验证

- [ ] folder CRUD 生效
- [ ] 拖拽改分组
- [ ] Ctrl+S 保存流程闭环
- [ ] 分享功能（如本期实现）有权限检查

## 完成标准

- [ ] 面板功能完整
- [ ] 后端接口通过集成测试
