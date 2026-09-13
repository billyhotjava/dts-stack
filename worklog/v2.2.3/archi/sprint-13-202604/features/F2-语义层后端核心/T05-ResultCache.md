# T05: ResultCache — Redis 缓存层

**优先级**: P1
**状态**: READY
**依赖**: T03

## 目标

给查询结果加一层 Redis 缓存，减少重复查询对数据库的压力。缓存 key 必须包含**用户安全上下文**，防止串用户。

## 技术设计

### 1. 缓存 key 组成

```
semantic:query:v1:<manifest_version>:<user_sec_hash>:<query_fingerprint>
```

- `manifest_version` — Sprint-13 ingest 时生成的 hash，manifest 变化则失效
- `user_sec_hash` = `sha256(user.security_level + "|" + sorted(user.dept_ids) + "|" + user.roles)`
- `query_fingerprint` = `sha256(canonicalize(queryRequest))`

### 2. canonicalize(queryRequest)

去除语义无关的差异：
- Map key 按字母序
- 数组里的 measures/dimensions/filters 按 id 排序
- 时间字面量归一化到 ISO 8601
- 空字段移除

这样 `{a:1, b:2}` 和 `{b:2, a:1}` 是同一个 key。

### 3. 缓存值

```json
{
  "columns": [...],
  "rows": [...],
  "meta_subset": { "row_count": ..., "sql_preview": "..." },
  "cached_at": "2026-04-22T10:00:00Z"
}
```

**不缓存**:
- `elapsed_ms`（执行时间，缓存命中时替换）
- `security_applied`（可能变化？保守不缓存，每次重算）

### 4. TTL 策略

| 查询类型 | TTL |
|---|---|
| 默认 | 5 分钟 |
| 带 `cache_hint: "fresh"` | 不缓存（读也不写） |
| 带 time filter `>= now()` | 1 分钟（避免"今天数据"被旧缓存挡住） |
| 含 orphaned metric | 不缓存 |

### 5. 失效机制

- **ManifestIngestor 成功后** → `DEL semantic:query:*:<old_manifest_version>:*`（用 SCAN 批量删）
- **ETL 后钩子**（未来）→ 按 model 粒度失效：key pattern 里加 `source_models` 字段
- 手动 admin 端点 → `POST /admin/semantic/cache/invalidate`

### 6. 大结果集的取舍

- 单个值 > 512KB 不缓存（避免 Redis memory 压力）
- 监控：缓存命中率、平均 value 大小、p99 读写延迟

### 7. 分层可选（本 Sprint 不实现）

未来可加 L1 Caffeine + L2 Redis。本 Sprint 只 L2 Redis，简单即可。

### 8. 统计面板

Redis 计数器：
- `semantic:stat:query_count:<yyyymmdd>`
- `semantic:stat:cache_hit:<yyyymmdd>`
- `semantic:stat:cache_miss:<yyyymmdd>`

供 ops 看板使用。

## 影响范围

| 类型 | 文件 |
|---|---|
| 新建 | `service/semantic/cache/ResultCache.java` |
| 新建 | `service/semantic/cache/ResultCacheKey.java`（canonicalize + hash） |
| 修改 | `SemanticQueryResource` 调用缓存 |
| 修改 | `ManifestIngestor` 注入失效钩子 |
| 测试 | `ResultCacheKeyTest`（canonicalize 正反例）、`ResultCacheIT` |

## 验证

- [ ] 同一 query 二次调用命中（response.meta.cache_hit = true）
- [ ] 两个字段顺序不同但语义相同的 query 命中同一缓存 key
- [ ] 不同用户（不同密级/部门）**不命中**彼此缓存
- [ ] manifest 重新 ingest 后缓存失效
- [ ] `cache_hint: fresh` 强制绕过
- [ ] 大结果集 > 512KB 不缓存（日志记录原因）
- [ ] 统计计数器正确增长

## 完成标准

- [ ] 缓存命中率统计可观测
- [ ] 覆盖率 ≥ 80%，canonicalize 函数的单测覆盖所有边界
- [ ] 文档记录失效策略，存 `assets/specs/05-cache-invalidation.md`
