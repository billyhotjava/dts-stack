# 数据源权限与 CSV 上传 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现三级权限体系（数据管理员/普通用户/未认证），CSV 上传表按用户隔离，固定上传到内置数据湖。

**Architecture:** 在 MetabaseAuth 中新增 requireDataAdmin() 方法解析 X-DTS-Roles 头判断角色；DatabaseUploadTableService 表名加入 username 前缀；前端根据 isDataAdmin 控制 UI 可见性。

**Tech Stack:** Java 21 / Spring Boot 3.4.5 / React / TypeScript / PostgreSQL

---

## 文件结构

| 文件 | 职责 | 操作 |
|------|------|------|
| `MetabaseAuth.java` | 权限检查工具类 | 修改：新增 requireDataAdmin()、isDataAdmin() |
| `UserResource.java` | 用户 API | 修改：/current 返回 isDataAdmin |
| `PlatformIntegrationResource.java` | 平台数据源 API | 修改：权限改为 requireDataAdmin |
| `DatabaseResource.java` | 数据库管理 API | 修改：管理端点改权限；新增 my-uploads、delete-upload-table |
| `DatabaseUploadTableService.java` | 上传表服务 | 修改：表名加 username；新增 listUserTables、dropUserTable |
| `analyticsApi.ts` | 前端 API 客户端 | 修改：新增 getCurrentUser、listMyUploads、deleteUploadTable |
| `DataPage.tsx` | 数据列表页 | 修改：按角色控制按钮 |
| `DatabaseNewPage.tsx` | 导入/上传页 | 修改：按角色控制标签页；去掉数据库选择器 |
| `UploadedDataEditor.tsx` | 上传编辑器组件 | 修改：去掉 databaseId prop |

---

### Task 1: MetabaseAuth — 新增 requireDataAdmin

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/support/MetabaseAuth.java`

- [ ] **Step 1: 添加 DATA_ADMIN_ROLES 常量和 requireDataAdmin 方法**

```java
// 在 MetabaseAuth 类中添加：
import java.util.Set;

// 常量
private static final Set<String> DATA_ADMIN_ROLES = Set.of(
    "ROLE_OP_ADMIN", "ROLE_INST_DATA_OWNER", "ROLE_INST_LEADER"
);

public static Optional<ResponseEntity<String>> requireDataAdmin(
        AnalyticsSessionService sessionService, HttpServletRequest request) {
    Optional<AnalyticsUser> user = sessionService.resolveUser(request);
    if (user.isEmpty()) {
        return Optional.of(ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated"));
    }
    if (user.orElseThrow().isSuperuser()) {
        return Optional.empty(); // superuser always passes
    }
    if (!hasDataAdminRole(request)) {
        return Optional.of(
                ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that."));
    }
    return Optional.empty();
}

public static boolean isDataAdmin(HttpServletRequest request) {
    String roles = request.getHeader("X-DTS-Roles");
    if (roles == null || roles.isBlank()) {
        return false;
    }
    for (String role : roles.split(",")) {
        if (DATA_ADMIN_ROLES.contains(role.trim())) {
            return true;
        }
    }
    return false;
}

private static boolean hasDataAdminRole(HttpServletRequest request) {
    return isDataAdmin(request);
}
```

- [ ] **Step 2: 编译验证**

Run: `cd source/dts-analytics && mvn compile -q`
Expected: 编译成功，无错误

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/support/MetabaseAuth.java
git commit -m "feat: add requireDataAdmin permission check in MetabaseAuth"
```

---

### Task 2: UserResource — /current 返回 isDataAdmin

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/UserResource.java`

- [ ] **Step 1: 修改 current 端点，返回 isDataAdmin 字段**

在 `current()` 方法中，获取 `isDataAdmin` 并加入响应：

```java
@GetMapping(path = "/current", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> current(HttpServletRequest request) {
    Optional<AnalyticsUser> user = sessionService.resolveUser(request);
    if (user.isEmpty()) {
        return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
    }
    Map<String, Object> result = toMetabaseUser(user.orElseThrow(), groupService, MetabaseLocale.resolve(request));
    result.put("is_data_admin", user.orElseThrow().isSuperuser() || MetabaseAuth.isDataAdmin(request));
    // 添加 X-DTS-User 作为平台用户名
    String platformUsername = request.getHeader("X-DTS-User");
    if (platformUsername != null && !platformUsername.isBlank()) {
        result.put("platform_username", platformUsername.trim());
    }
    return ResponseEntity.ok(result);
}
```

需要在文件头部添加 import：
```java
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
```

- [ ] **Step 2: 编译验证**

Run: `cd source/dts-analytics && mvn compile -q`
Expected: 编译成功

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/UserResource.java
git commit -m "feat: add is_data_admin and platform_username to /api/user/current"
```

---

### Task 3: PlatformIntegrationResource — 权限改为 dataAdmin

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/PlatformIntegrationResource.java`

- [ ] **Step 1: dataSources 端点改为 requireDataAdmin**

将第 31 行的 `requireUser` 改为 `requireDataAdmin`：

```java
@GetMapping(path = "/data-sources", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> dataSources(HttpServletRequest request) {
    Optional<ResponseEntity<String>> auth = MetabaseAuth.requireDataAdmin(sessionService, request);
    if (auth.isPresent()) {
        return auth.get();
    }
    // ... rest unchanged
```

- [ ] **Step 2: 编译验证**

Run: `cd source/dts-analytics && mvn compile -q`
Expected: 编译成功

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/PlatformIntegrationResource.java
git commit -m "feat: restrict platform data-sources endpoint to dataAdmin role"
```

---

### Task 4: DatabaseResource — 管理端点改权限 + 新增上传管理 API

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/DatabaseResource.java`

- [ ] **Step 1: 管理端点从 requireSuperuser 改为 requireDataAdmin**

将以下端点的权限检查从 `requireSuperuser` 改为 `requireDataAdmin`：
- `create()` (POST /api/database)
- `update()` (PUT /api/database/{id})
- `delete()` (DELETE /api/database/{id})
- `syncSchema()` (POST /api/database/{id}/sync_schema)
- `validateConnection()` (POST /api/database/validate)
- `rescanValues()`
- `discardValues()`
- `persist()`
- `unpersist()`
- `addSampleDatabase()`

每处将 `MetabaseAuth.requireSuperuser(sessionService, ...)` 替换为 `MetabaseAuth.requireDataAdmin(sessionService, ...)`。

- [ ] **Step 2: 新增 GET /api/database/{dbId}/my-uploads 端点**

在 `uploadTable` 方法之后添加：

```java
@GetMapping(path = "/{dbId}/my-uploads", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> myUploads(@PathVariable("dbId") long dbId, HttpServletRequest request) {
    Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
    if (auth.isPresent()) {
        return auth.get();
    }
    if (!databaseRepository.existsById(dbId)) {
        return ResponseEntity.notFound().build();
    }
    String username = request.getHeader("X-DTS-User");
    if (username == null || username.isBlank()) {
        return ResponseEntity.ok(List.of());
    }
    String prefix = "upload_" + DatabaseUploadTableService.sanitizeUsername(username) + "_";
    List<Map<String, Object>> tables = tableRepository
            .findAllByDatabaseIdAndSchemaNameOrderByNameAsc(dbId, DatabaseUploadTableService.SCHEMA)
            .stream()
            .filter(t -> t.isActive() && t.getName() != null && t.getName().startsWith(prefix))
            .map(t -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", t.getId());
                item.put("name", t.getName());
                item.put("display_name", t.getDisplayName());
                item.put("schema", t.getSchemaName());
                item.put("created_at", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
                return item;
            })
            .toList();
    return ResponseEntity.ok(tables);
}
```

- [ ] **Step 3: 新增 DELETE /api/database/{dbId}/upload-table/{tableName} 端点**

```java
@DeleteMapping(path = "/{dbId}/upload-table/{tableName}")
public ResponseEntity<?> deleteUploadTable(
        @PathVariable("dbId") long dbId,
        @PathVariable("tableName") String tableName,
        HttpServletRequest request) {
    Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
    if (auth.isPresent()) {
        return auth.get();
    }
    if (!databaseRepository.existsById(dbId)) {
        return ResponseEntity.notFound().build();
    }
    String username = request.getHeader("X-DTS-User");
    if (username == null || username.isBlank()) {
        return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("无法识别用户");
    }
    String prefix = "upload_" + DatabaseUploadTableService.sanitizeUsername(username) + "_";
    if (!tableName.startsWith(prefix)) {
        return ResponseEntity.status(403).body(Map.of("errors", Map.of("table", "只能删除自己上传的表")));
    }
    try {
        uploadTableService.dropUploadTable(dbId, tableName);
        return ResponseEntity.noContent().build();
    } catch (SQLException e) {
        return ResponseEntity.internalServerError().body(Map.of("error", "删除表失败: " + e.getMessage()));
    }
}
```

- [ ] **Step 4: 修改 uploadTable 端点，传入 username**

```java
@PostMapping(path = "/{dbId}/upload-table", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> uploadTable(
        @PathVariable("dbId") long dbId,
        @RequestBody UploadTableRequest body,
        HttpServletRequest request) {
    Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
    if (auth.isPresent()) {
        return auth.get();
    }
    if (!databaseRepository.existsById(dbId)) {
        return ResponseEntity.notFound().build();
    }
    String username = request.getHeader("X-DTS-User");
    if (username == null || username.isBlank()) {
        return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("无法识别用户");
    }
    if (body == null || body.tableName() == null || body.tableName().isBlank()) {
        return ResponseEntity.badRequest().body(Map.of("errors", Map.of("tableName", "表名不能为空")));
    }
    if (body.columns() == null || body.columns().isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of("errors", Map.of("columns", "列定义不能为空")));
    }

    List<DatabaseUploadTableService.ColumnDef> columns = body.columns().stream()
            .map(c -> new DatabaseUploadTableService.ColumnDef(c.name(), c.displayName(), c.type()))
            .toList();

    try {
        DatabaseUploadTableService.UploadResult result = uploadTableService.uploadTable(
                dbId, username, body.tableName(), columns, body.rows());
        return ResponseEntity.ok(Map.of(
                "tableName", result.tableName(),
                "schema", result.schema(),
                "rowCount", result.rowCount()));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("errors", Map.of("database", e.getMessage())));
    } catch (SQLException e) {
        return ResponseEntity.internalServerError().body(Map.of("error", "上传表失败: " + e.getMessage()));
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `cd source/dts-analytics && mvn compile -q`
Expected: 编译失败（DatabaseUploadTableService 签名尚未更新），Task 5 修复

- [ ] **Step 6: Commit（与 Task 5 一起提交）**

---

### Task 5: DatabaseUploadTableService — 表名加 username 前缀 + 新增管理方法

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/DatabaseUploadTableService.java`

- [ ] **Step 1: 将 SCHEMA 改为 public 常量，新增 sanitizeUsername 方法**

```java
// 将 private 改为包可见（同包的 DatabaseResource 需要引用）
static final String SCHEMA = "upload";

public static String sanitizeUsername(String username) {
    if (username == null || username.isBlank()) {
        return "anonymous";
    }
    String sanitized = username.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
    if (sanitized.isEmpty()) {
        return "anonymous";
    }
    if (sanitized.length() > 20) {
        sanitized = sanitized.substring(0, 20).replaceAll("_$", "");
    }
    return sanitized;
}
```

- [ ] **Step 2: 修改 uploadTable 签名，加入 username 参数**

```java
public UploadResult uploadTable(long databaseId, String username, String tableName,
        List<ColumnDef> columns, List<List<Object>> rows) throws SQLException {
    String sanitized = sanitizeTableName(username, tableName);
    String fullTableName = SCHEMA + ".\"" + sanitized + "\"";
    // ... rest of method body unchanged
}
```

- [ ] **Step 3: 修改 sanitizeTableName，加入 username 前缀**

```java
static String sanitizeTableName(String username, String name) {
    if (name == null || name.isBlank()) {
        name = "untitled";
    }
    String sanitized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fff]", "_");
    sanitized = sanitized.replaceAll("_+", "_").replaceAll("^_|_$", "");
    if (sanitized.isEmpty()) {
        sanitized = "untitled";
    }
    if (sanitized.length() > MAX_TABLE_NAME_LENGTH) {
        sanitized = sanitized.substring(0, MAX_TABLE_NAME_LENGTH);
        sanitized = sanitized.replaceAll("_$", "");
    }
    String prefix = "upload_" + sanitizeUsername(username) + "_" + LocalDate.now().format(DATE_FMT) + "_";
    return prefix + sanitized;
}
```

- [ ] **Step 4: 新增 dropUploadTable 方法**

```java
public void dropUploadTable(long databaseId, String tableName) throws SQLException {
    HikariDataSource dataSource = dataSourceRegistry.get(databaseId);
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
        stmt.execute("DROP TABLE IF EXISTS " + SCHEMA + ".\"" + tableName + "\"");
        log.info("Dropped upload table {}.\"{}\" from database {}", SCHEMA, tableName, databaseId);
    }
    metadataSyncService.syncDatabaseSchema(databaseId);
}
```

- [ ] **Step 5: 更新 JavaDoc 注释**

将方法注释中的 "biadmin schema" 改为 "upload schema"。

- [ ] **Step 6: 编译验证**

Run: `cd source/dts-analytics && mvn compile -q`
Expected: 编译成功

- [ ] **Step 7: Commit（合并 Task 4 和 Task 5）**

```bash
git add source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/DatabaseResource.java \
      source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/DatabaseUploadTableService.java
git commit -m "feat: add dataAdmin permission, user-scoped upload tables, my-uploads and delete-upload-table APIs"
```

---

### Task 6: analyticsApi.ts — 新增前端 API 方法

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/api/analyticsApi.ts`

- [ ] **Step 1: 新增 CurrentUser 类型**

在 `DatabaseListItem` 类型附近添加：

```typescript
export type CurrentUser = {
	id: number;
	email?: string;
	first_name?: string;
	last_name?: string;
	common_name?: string;
	is_superuser?: boolean;
	is_data_admin?: boolean;
	platform_username?: string;
};

export type MyUploadItem = {
	id: number;
	name: string;
	display_name?: string;
	schema?: string;
	created_at?: string;
};
```

- [ ] **Step 2: 在 analyticsApi 对象中新增方法**

在 `uploadTable` 方法附近添加：

```typescript
getCurrentUser: () => fetchJson<CurrentUser>("/analytics/api/user/current"),
listMyUploads: (dbId: number | string) =>
	fetchJson<MyUploadItem[]>(`/analytics/api/database/${encodeURIComponent(String(dbId))}/my-uploads`),
deleteUploadTable: (dbId: number | string, tableName: string) =>
	requestJson<void>(
		`/analytics/api/database/${encodeURIComponent(String(dbId))}/upload-table/${encodeURIComponent(tableName)}`,
		"DELETE"
	),
```

- [ ] **Step 3: 编译验证**

Run: `cd source/dts-analytics-webapp/modern && npx vite build 2>&1 | tail -5`
Expected: built successfully

- [ ] **Step 4: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/api/analyticsApi.ts
git commit -m "feat: add getCurrentUser, listMyUploads, deleteUploadTable API methods"
```

---

### Task 7: DataPage.tsx — 按角色控制 UI

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/DataPage.tsx`

- [ ] **Step 1: 加载当前用户信息**

在组件顶部添加状态和 useEffect：

```typescript
import { analyticsApi, type DatabaseListItem, type CurrentUser } from "../api/analyticsApi";

// 在 DataPage 组件内：
const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);

useEffect(() => {
	analyticsApi.getCurrentUser().then(setCurrentUser).catch(() => {});
}, []);

const isDataAdmin = currentUser?.is_data_admin || currentUser?.is_superuser || false;
```

- [ ] **Step 2: 修改页面头部按钮**

将固定的"导入平台数据源"按钮改为按角色显示：

```tsx
<PageHeader
	title={t(locale, "data.title")}
	actions={
		isDataAdmin ? (
			<Link to="/data/new">
				<Button type="primary" icon={<PlusIcon />}>
					{t(locale, "data.add")}
				</Button>
			</Link>
		) : (
			<Link to="/data/new?tab=other">
				<Button type="primary" icon={<PlusIcon />}>
					上传数据
				</Button>
			</Link>
		)
	}
/>
```

- [ ] **Step 3: 非数据管理员隐藏删除按钮（非系统库也不能删）**

将删除按钮的条件从 `!db.is_system` 改为 `!db.is_system && isDataAdmin`：

```tsx
{!db.is_system && isDataAdmin && (
	<div style={{ display: "flex", gap: "var(--spacing-xs)", marginTop: "var(--spacing-md)", justifyContent: "flex-end" }}>
		<Button type="text" size="small" icon={<TrashIcon />}
			onClick={() => setConfirmDeleteId(db.id)}
			style={{ color: "var(--color-error)" }}>
			{t(locale, "data.delete")}
		</Button>
	</div>
)}
```

- [ ] **Step 4: 编译验证**

Run: `cd source/dts-analytics-webapp/modern && npx vite build 2>&1 | tail -5`
Expected: built successfully

- [ ] **Step 5: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/DataPage.tsx
git commit -m "feat: show data admin controls based on user role"
```

---

### Task 8: DatabaseNewPage.tsx — 按角色控制标签页 + 去掉数据库选择器

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/DatabaseNewPage.tsx`

- [ ] **Step 1: 加载当前用户 + 数据湖 ID**

在组件顶部添加：

```typescript
import { analyticsApi, type PlatformDataSourceItem, type CurrentUser } from "../api/analyticsApi";

// 在组件内：
const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
const [dataLakeId, setDataLakeId] = useState<number | null>(null);

useEffect(() => {
	analyticsApi.getCurrentUser().then(setCurrentUser).catch(() => {});
}, []);

const isDataAdmin = currentUser?.is_data_admin || currentUser?.is_superuser || false;
```

- [ ] **Step 2: 从 URL 参数读取初始 tab，非管理员默认 other**

```typescript
const searchParams = new URLSearchParams(window.location.search);
const initialTab = searchParams.get('tab') === 'other' ? 'other' : 'platform';
const [activeTab, setActiveTab] = useState<'platform' | 'other'>(
	isDataAdmin ? initialTab : 'other'
);

// 当 currentUser 加载完成后，如果非管理员则强制切到 other
useEffect(() => {
	if (currentUser && !isDataAdmin) {
		setActiveTab('other');
	}
}, [currentUser, isDataAdmin]);
```

- [ ] **Step 3: 自动查找数据湖 ID**

修改已有的 `listDatabases` useEffect：

```typescript
useEffect(() => {
	analyticsApi.listDatabases().then((list: any) => {
		const dbs = (Array.isArray(list) ? list : list?.data || []);
		const dbList = dbs.map((d: any) => ({ id: d.id, name: d.name }));
		setDatabases(dbList);
		// 找到内置数据湖
		const lake = dbs.find((d: any) => d.is_system === true);
		if (lake) {
			setDataLakeId(lake.id);
			setSelectedDbId(lake.id);
		} else if (dbList.length > 0) {
			setSelectedDbId(dbList[0].id);
		}
	}).catch(() => {});
}, []);
```

- [ ] **Step 4: 隐藏非管理员的平台数据源标签**

在标签页渲染处，仅管理员显示平台标签：

```tsx
<div style={{ display: 'flex', borderBottom: '1px solid var(--color-border)', marginBottom: 'var(--spacing-md)' }}>
	{isDataAdmin && (
		<button type="button" onClick={() => setActiveTab('platform')}
			style={{ /* ... existing styles ... */ }}>
			{t(locale, 'data.tabPlatform')}
		</button>
	)}
	<button type="button" onClick={() => setActiveTab('other')}
		style={{ /* ... existing styles ... */ }}>
		{t(locale, 'data.tabOther')}
	</button>
</div>
```

- [ ] **Step 5: 去掉"其他数据源"中的数据库选择器，固定使用数据湖**

将整个 `<div>` 数据库选择器块替换为：

```tsx
{activeTab === 'other' && (
	<div>
		<p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)', marginBottom: 'var(--spacing-md)' }}>
			{t(locale, 'data.uploadDesc')}
		</p>

		{dataLakeId ? (
			<UploadedDataEditor
				databaseId={dataLakeId}
				onComplete={(result: { tableName: string; schema: string; rowCount: number }) => {
					setOkMessage(`导入成功: ${result.tableName} (${result.rowCount} 行)`);
					if (dataLakeId) {
						analyticsApi.syncDatabaseSchema(dataLakeId).then(() => {
							navigate(`/data/${dataLakeId}`, { replace: true });
						}).catch(() => {
							navigate(`/data/${dataLakeId}`, { replace: true });
						});
					}
				}}
			/>
		) : (
			<EmptyState
				title="数据湖未就绪"
				description="内置数据湖尚未初始化，请联系管理员。"
			/>
		)}
	</div>
)}
```

- [ ] **Step 6: 仅管理员加载平台数据源**

将 `reload()` 的调用改为仅管理员触发：

```typescript
useEffect(() => {
	if (isDataAdmin) {
		reload();
	}
}, [isDataAdmin]);
```

去掉原来的 `useEffect(() => { reload(); }, []);`。

- [ ] **Step 7: 编译验证**

Run: `cd source/dts-analytics-webapp/modern && npx vite build 2>&1 | tail -5`
Expected: built successfully

- [ ] **Step 8: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/DatabaseNewPage.tsx
git commit -m "feat: role-based tab visibility, auto-select data lake for uploads"
```

---

### Task 9: 集成验证与最终提交

- [ ] **Step 1: 全量 Java 编译**

Run: `cd /opt/prod/s10/s10-stack && mvn compile -q 2>&1 | tail -5`
Expected: 编译成功

- [ ] **Step 2: 全量前端构建**

Run: `cd source/dts-analytics-webapp/modern && npx vite build 2>&1 | tail -5`
Expected: built successfully

- [ ] **Step 3: 检查所有变更文件**

Run: `git diff --stat`

应包含以下文件：
- `MetabaseAuth.java` — 新增 requireDataAdmin/isDataAdmin
- `UserResource.java` — /current 加 is_data_admin
- `PlatformIntegrationResource.java` — requireDataAdmin
- `DatabaseResource.java` — 管理端点改权限 + 新端点
- `DatabaseUploadTableService.java` — username 前缀 + dropUploadTable
- `analyticsApi.ts` — 新 API 方法和类型
- `DataPage.tsx` — 角色控制 UI
- `DatabaseNewPage.tsx` — 标签页控制 + 数据湖固定

- [ ] **Step 4: 最终合并提交（如未逐步提交）**

```bash
git add -A
git commit -m "feat: data source permission model with dataAdmin role and user-scoped CSV uploads"
```
