# T01: JoinGraphRegistry

**优先级**: P0
**状态**: READY
**依赖**: F2/T01

## 目标

把 `gov_join_edge` 表加载为内存中的有向图结构，回答编译器和前端画布的核心问题：
- A 能否 join 到 B？
- 走哪条路径？
- 路径上是否有 fanout？
- 路径上是否需要审批？

## 技术设计

### 1. 数据结构

```java
public class JoinGraph {
    private Map<String, ModelNode> nodes;               // model.name → node
    private Map<String, List<JoinEdge>> outgoing;       // model.name → edges
    // ...
}

public record ModelNode(String name, String subjectArea, SecurityLevel level, boolean exposed) {}

public record JoinEdge(
    String from,
    String to,
    JoinType type,
    String onTemplate,          // 未渲染模板
    RelationshipMode mode,      // INNER / LEFT
    boolean fanoutWarning,
    boolean approvalRequired
) {}

public enum JoinType { MANY_TO_ONE, ONE_TO_MANY, MANY_TO_MANY, ONE_TO_ONE }
```

### 2. Registry 服务

```java
@Service
public class JoinGraphRegistry {
    private final AtomicReference<JoinGraph> current = new AtomicReference<>();

    @EventListener(ManifestIngestedEvent.class)
    public void reload() { ... }

    public List<JoinPath> pathsFromTo(String from, String to, int maxHops) { ... }
    public Optional<JoinEdge> directEdge(String from, String to) { ... }
    public JoinGraph snapshot() { return current.get(); }
}
```

### 3. 路径发现

本 Sprint **只实现一跳**（max_hops = 1）：
- `directEdge(from, to)` 返回唯一 edge，否则 absent
- 多跳路径（`from → mid → to`）schema 层预留，实现放 Sprint-14

若前端请求多跳：编译器返回 400 "join path not supported (max 1 hop)"。

### 4. 一致性保证

- 加载完整新图到临时对象，AtomicReference CAS 替换
- 期间进行的查询继续用旧快照
- Reload 失败不替换（保留旧图）+ 报警

### 5. 热重载钩子

- `ManifestIngestedEvent` 触发（F2/T01 发）
- 启动时从 `gov_join_edge` 表初始加载
- 提供 admin 端点强制 reload：`POST /admin/semantic/graph/reload`

### 6. 可视化 API 对接

`SemanticMetaResource.getJoinGraph()` (F2/T02) 直接从 registry 拿快照序列化为 JSON。

### 7. 校验

加载时对每条 edge 做：
- `from` / `to` model 都存在
- `on_clause` 模板语法合法（占位变量只能是 `{{this}}` / `{{to}}`）
- JoinType 合法
- `mode` 合法
- 同一对 (from, to) 不允许有 2 条不同的 on_clause（后续支持多路径时再放开）

违反则 ingest 失败（在 F2/T01 的 validator 就要拦截）。

## 影响范围

| 类型 | 文件 |
|---|---|
| 新建 | `service/semantic/graph/JoinGraph.java` |
| 新建 | `service/semantic/graph/JoinGraphRegistry.java` |
| 新建 | `service/semantic/graph/JoinPath.java` |
| 测试 | `JoinGraphRegistryTest`（加载、路径、CAS reload） |

## 验证

- [ ] 启动时从 DB 加载，3 张示例 model 的关系正确建立
- [ ] `directEdge("ads_sales_daily", "dim_customer")` 返回 edge
- [ ] `directEdge("ads_sales_daily", "dim_irrelevant")` 返回 empty
- [ ] ingest 后触发 reload，新 edge 可见
- [ ] reload 失败时旧图仍可用
- [ ] 多跳请求返回 400（暂不支持）

## 完成标准

- [ ] JoinGraph 序列化 JSON 符合 F1-T02 中 `/graph` 端点响应结构
- [ ] 单元测试覆盖率 ≥ 85%
- [ ] 文档更新 F3 architecture.md 里的"join 路径发现"章节
