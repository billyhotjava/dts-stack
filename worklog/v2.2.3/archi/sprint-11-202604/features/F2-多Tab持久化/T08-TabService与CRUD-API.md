# T08: SqlIdeTabService + CRUD API

**优先级**: P0
**状态**: READY
**依赖**: T07

## 目标

实现 Tab 的后端 CRUD 接口和批量同步接口，支持前端防抖批量推送。

## 技术设计

### API 清单

| 方法 | 路径 | 作用 |
|---|---|---|
| `GET` | `/api/sql/v2/tabs` | 列出当前用户所有 Tab，按 `sort_order` 升序 |
| `POST` | `/api/sql/v2/tabs` | 创建 Tab，返回 id |
| `PATCH` | `/api/sql/v2/tabs/{id}` | 局部更新（sql_text / cursor / title 等） |
| `DELETE` | `/api/sql/v2/tabs/{id}` | 关闭 Tab |
| `POST` | `/api/sql/v2/tabs/batch` | 批量 upsert（请求体为 `[{id?, ...fields}]`） |

### 权限

- 所有操作必须校验 `tab.user_id == current_user`
- 任何跨用户访问返回 404（不暴露存在性）

### 并发控制

- `updated_at` 作为乐观锁字段
- PATCH 请求体需带 `updatedAt`，服务端比较，若 DB 已更新过（`updatedAt` 更大）→ 返回 409 Conflict + 最新数据
- 前端接到 409 提示"Tab 在其他地方被修改，已刷新"

### 数量限制

- 单用户上限 30 个 Tab
- `POST` 或 `batch` 超过时返回 429 + 错误码 `TOO_MANY_TABS`

### 审计

新增 `SQL_IDE_TAB_SAVE` 审计类型，字段：`tabId`, `sqlTextHash`（SHA-256 前 16 位，防审计库膨胀）

### SqlIdeTabService

```java
public interface SqlIdeTabService {
    List<SqlIdeTabDto> listByUser(String userId);
    SqlIdeTabDto create(String userId, CreateTabRequest req);
    SqlIdeTabDto patch(String userId, UUID id, PatchTabRequest req);
    void delete(String userId, UUID id);
    List<SqlIdeTabDto> batchUpsert(String userId, List<UpsertTabRequest> reqs);
}
```

## 影响范围

- `web/rest/sql/SqlIdeResource.java` 新增 5 个端点
- `service/sql/SqlIdeTabService.java` + 实现类
- `service/sql/dto/*` DTO 定义
- 审计枚举新增 `SQL_IDE_TAB_SAVE`

## 验证

- [ ] CRUD 全部通过 `MockMvc` 集成测试
- [ ] 跨用户访问返回 404
- [ ] 乐观锁冲突返回 409
- [ ] 超过 30 个 Tab 返回 429
- [ ] 批量 upsert 支持 50 条以内原子事务
- [ ] 审计日志写入正确

## 完成标准

- [ ] 5 个端点全部可用
- [ ] 集成测试覆盖权限、乐观锁、限额
- [ ] `sqlTextHash` 审计字段生效
