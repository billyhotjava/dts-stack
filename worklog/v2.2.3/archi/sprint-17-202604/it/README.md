# Sprint-17 集成测试证据

## 2026-04-26 hotfix（在 v2.2.3 主线直推）

UAT 反馈"我的概览"页面链接基本不可用、机构显示编码而非名称、"我常用的报表"为 0，复盘后定位三个 P0：

| Bug | 位置 | 修复 |
|---|---|---|
| **B1** "我常用的报表"全部跳 `/reports/{uuid}` 404 | `TopReportsBlock.tsx`、`LeaderOverviewResponse.TopReport` 缺 url/engine | 后端 DTO+JPQL+service 把 `r.url, r.engine` 透传；前端改用 `resolveBiLinkForOpen(item.url, item.engine)` |
| **B2** 部门标签显示机构编码 | `DeptSelect.tsx` 只渲染 `deptCode` | `KeycloakAuthResource` 登录响应补 `dept_name`；`userStore.resolveDeptNameFromRaw`、`useWorkbenchRole.deptName`、`DeptSelect` 显示名称（缺名时回退到 org tree 解码） |
| **B3a** 用户无 visit 时永远是 0 | `WorkbenchLeaderOverviewService.computeTopReports` MINE 模式只查 visit 表 | MINE 空 visit 时回退到 `BiReportLink.findRecentBySourceForFallback("SCREEN_SYNC", topN)`，按 lastVisitedAt desc 列出 reconcile 出的大屏；visits 报 0 标识"未访问" |

### 验证

| 测试 | 用例数 | 状态 |
|---|---|---|
| Backend `ScreenReportLinkSyncServiceTest` | 7 | ✅ PASS |
| Backend `ScreenSyncClientTest` | 3 | ✅ PASS |
| Backend `WorkbenchLeaderOverviewServiceTest` (targeted) | 4 | ✅ PASS |
| Backend `WorkbenchRoleResolverTest` | 4 | ✅ PASS |
| Backend `ClassificationMapperTest` | 11 | ✅ PASS |
| Backend `BiReportLinkServiceTest` | 2 | ✅ PASS |
| Frontend `TopReportsBlock.test.tsx` | 8 | ✅ PASS |
| Frontend full workbench + screens | 114 | ✅ PASS |
| `pnpm tsc --noEmit` / `mvn -DskipTests compile` | — | ✅ PASS |

预存失败（`WorkbenchAuditRateLimiterTest`、`WorkbenchLeaderOverviewServiceTest#computeKpis_ALL_with_bizDomain_passes_filter_to_all_aggregates`）状态未变，不在本次范围。

### Ops 仍需在真实环境验证

1. 登录后顶部"本部门："应显示中文机构名（不是 `OPS-NJ-01` 等编码）
2. "我常用的报表"在用户从未访问大屏时，应直接显示 reconcile 出的大屏标题（visits 显示 0）
3. 点击列表中任一行，应在新窗口打开 `/bi/screens/{id}/preview` 而非 404

## 2026-04-26 后续 hotfix（无需 SQL，重建容器即可）

UAT 重建容器后仍报错 `column bi_report_link.source does not exist` →
原 changeset `20260425_02_bi_report_link_source.xml` 因 precondition 评估
被 `onFail=MARK_RAN` 吞掉、databasechangelog 已记录但 column 没建。
两层修复让"重建容器即恢复"成立：

| 层 | 措施 |
|---|---|
| **Liquibase** | 新增 `20260426_01_bi_report_link_source_retry.xml`，新 changeset id 不会被旧记录拦截，使用 PostgreSQL `ADD COLUMN IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` 幂等 raw SQL，无 precondition |
| **Code 兜底** | `BiReportLink.source` 字段保持 `@Transient`（不参与 SELECT/INSERT），reconcile + fallback 用 `code LIKE 'screen-%'` 识别同步行，所以即使 retry changeset 也失败，应用仍能工作 |

**部署路径**：
1. 拉新镜像
2. 重建容器（Liquibase 启动时自动跑 retry changeset 建出 source 列）
3. 完毕

`hotfix-add-bi-report-link-source.sql` 仍保留作为应急后备。

## （以下保留 sprint-17 原始 IT 证据）

# Sprint-17 集成测试证据（原始）

## 自动化测试结果（已执行）

### 后端

| 测试 | 用例数 | 状态 |
|---|---|---|
| `ScreenReportLinkSyncServiceTest` (dts-platform) | 7 | ✅ PASS |
| `ScreenSyncClientTest` (dts-platform) | 3 | ✅ PASS |
| `InternalScreenResourceTest` (dts-analytics) | 3 | ✅ PASS |
| `ClassificationMapperTest` (Sprint-15 回归) | 11 | ✅ PASS |
| `WorkbenchRoleResolverTest` (Sprint-15 回归) | 4 | ✅ PASS |

### 前端

| 测试 | 用例数 | 状态 |
|---|---|---|
| `useScreenVisitTracker.test.ts` | 5 | ✅ PASS |
| `tsc --noEmit` | — | ✅ PASS |

### 已知预存失败（与 Sprint-17 无关）

baseline (commit `99da454bb`) 即已失败，sprint-17 改动未引入或修复：

| 测试 | 失败原因 | 引入 commit |
|---|---|---|
| `WorkbenchLeaderOverviewServiceTest#computeKpis_ALL_with_bizDomain_passes_filter_to_all_aggregates` | mock 期待 `countVisitsForAll` 调 1 次，service 现在调 2 次（本期 + 上期 MoM） | `53c0c6534 fix(sprint-15): harden leader-overview workbench` |
| `WorkbenchAuditRateLimiterTest#allows_up_to_60_calls_per_minute_then_blocks_61st` | Caffeine 时间敏感 flaky | sprint-15 时已有 |

按 sprint-workflow，这两条不在 sprint-17 修复范围；建议作为 sprint-18 follow-up（更新 mock 期望或将其改为 1+ 次匹配）。

## 端到端验证（待 ops 环境执行）

需在带 dts-platform + dts-analytics + dts-platform-webapp 的环境跑：

### Step 1 — 启动配置

dts-platform 环境变量：
```
DTS_ANALYTICS_BASE_URL=http://dts-analytics:8084
```

服务名默认 `dts-platform`（已在 `DtsAnalyticsProperties.serviceName` 配好）。

### Step 2 — 启动后 60s 内验证 reconcile

```bash
# 看日志
journalctl -u dts-platform -f | grep "screen reconcile"
# 应见: screen reconcile: created=N updated=0 archived=0

# 数据库验证
psql -d dtsPlatform -c "SELECT code, title, enabled, source FROM bi_report_link WHERE source='SCREEN_SYNC' ORDER BY code LIMIT 10;"
# 应见 N 条 code 以 'screen-' 开头的行
```

### Step 3 — 创建/归档大屏，下次 reconcile 验证

```bash
# 在 dts-bi UI 创建一个新大屏，命名"测试大屏 17"，分级 INTERNAL
# 等到下一个 cron tick（cron `0 17 * * * *`，下个整点的第 17 分钟）
# 或重启 dts-platform 触发 onReady tick

psql -d dtsPlatform -c "SELECT code, title, enabled FROM bi_report_link WHERE title='测试大屏 17';"
# 应见 enabled=true
```

归档同一大屏后再 reconcile：
```sql
SELECT code, title, enabled FROM bi_report_link WHERE title='测试大屏 17';
-- 应见 enabled=false
```

### Step 4 — Preview 埋点验证

1. 登录 dts-platform-webapp 测试账号
2. 进入 `/bi/screens/{id}/preview`
3. 停留 ≥4 秒
4. 浏览器 Network 面板看到 `POST /api/reports/visit` 200

```bash
psql -d dtsPlatform -c "SELECT user_login, report_id, visited_at FROM bi_report_visit ORDER BY visited_at DESC LIMIT 3;"
# 应见刚才停留的记录
```

### Step 5 — Leader Overview 显示验证

切换工作台 → "我的概览" → "我常用的报表"
- 期望：显示该大屏的标题、最近访问时间
- 30s 内重复进出同一大屏，仅 1 条 visit（防抖生效）

### 产物（建议留存到本目录）

- `reconcile.log` — Step 2 日志片段
- `db-check.sql` — Step 2-3 SQL + 输出
- `visit-network.png` — Step 4 浏览器截图
- `leader-overview.png` — Step 5 截图
- `dedupe.txt` — Step 5 防抖测试 visit 表对比

## 状态

- 自动化测试：✅ PASS（10 个新测试 + Sprint-15 非 flaky 部分回归通过）
- E2E：⏸️ 待 ops 环境验证（沙盒无法启 dts-bi）
