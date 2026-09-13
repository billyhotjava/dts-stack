# T03: 删除 WorkbenchService 收藏方法 + Resource 端点

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

`WorkbenchService` 删除：
- `listFavorites`、`createFavorite`、`updateFavorite`、`deleteFavorite`
- `applyFavorite` 私有 helper
- `FavoriteRequest` 内部 record
- `PortalUserFavoriteRepository favoriteRepository` 字段与构造器注入

`WorkbenchResource` 删除：
- `GET /favorites`、`POST /favorites`、`PUT /favorites/{id}`、`DELETE /favorites/{id}` 四个 handler
- `PortalUserFavorite` 的 import

## 技术设计

### WorkbenchService.java 修改

```java
// 删除字段和构造参数
-    private final PortalUserFavoriteRepository favoriteRepository;

public WorkbenchService(
    CatalogDatasetRepository datasetRepository,
    CatalogSchemaDriftEventRepository driftRepository,
    GovQualityRunRepository qualityRunRepository,
    DatasetDataAccessApprovalService accessApprovalService,
    CatalogDatasetAccessRequestRepository accessRequestRepository,
-   PortalUserFavoriteRepository favoriteRepository,
    ObjectMapper objectMapper
) { ... }
```

删除方法：
```java
-    public List<PortalUserFavorite> listFavorites(String userLogin) { ... }
-    @Transactional public PortalUserFavorite createFavorite(...) { ... }
-    @Transactional public PortalUserFavorite updateFavorite(...) { ... }
-    @Transactional public void deleteFavorite(...) { ... }
-    private void applyFavorite(...) { ... }
-    public record FavoriteRequest(...) {}
```

保留：`overview`、`todoItems`、`buildDriftTitle`、`normalize`、`normalizeUpper`。

检查 `objectMapper` 是否还被用到（`applyFavorite` 是唯一消费方）——**是**，所以随之删除 `ObjectMapper` 注入：

```java
-    private final ObjectMapper objectMapper;
```

同时删除 `import com.fasterxml.jackson.databind.ObjectMapper;` 和相关未用 import（如 `Locale`、`UUID` 若其他地方不用）。

### WorkbenchResource.java 修改

```java
-import com.yuzhi.dts.platform.domain.portal.PortalUserFavorite;
-import java.util.UUID;
```

删除四个 handler：`favorites()` / `createFavorite(...)` / `updateFavorite(...)` / `deleteFavorite(...)`。保留 `overview()` 和 `todos()`。

### 注意

- `WorkbenchResource` 上的 `@Transactional` 是类级别，保留即可；`overview` / `todos` 都是读取。
- `@RequestBody WorkbenchService.FavoriteRequest` 引用被一并删除，无残留。

## 影响范围

- `WorkbenchService.java`（删方法、删字段、删注入）
- `WorkbenchResource.java`（删 4 个 handler、删 import）

## 验证

- [ ] `./mvnw -pl source/dts-platform compile` 通过。
- [ ] `grep -rn "FavoriteRequest\|PortalUserFavorite" source/dts-platform/src/` 无结果。
- [ ] 启动服务：`curl -i http://localhost:8080/api/workbench/favorites` 返回 404（Spring 默认 error handler）。
- [ ] `WorkbenchResource` 相关 IT / MockMvcTest 若有测到 favorites，需一并删除测试用例或转为测试 "favorites endpoint no longer exists"（返回 404）。

## 完成标准

- [ ] Service + Resource 两文件里零 favorite 残留。
- [ ] 新建 `WorkbenchResourceNoFavoritesTest`（可选）断言 `/favorites` 404。
- [ ] 后端启动无 bean 注入错误（因 Repository 已在 T02 删除，这里必须同步清掉 `favoriteRepository` 参数）。
