# Sprint-11 灰度上线 SOP

## Feature Flag

- 环境变量：`WEBAPP_ENABLE_SQL_IDE_V2`（webapp 容器）和 `DTS_SQL_IDE_V2_ENABLED`（平台容器，保留未来 gate 用）
- 默认：`false`
- 影响：`/explore/workbench` 路由在启用时指向 `SqlIdePage`，关闭时回到 `QueryWorkbenchPage`

## 阶段

### 阶段 0 — 内部（<10 人，第 1-7 天）
1. 在 dev/stage 环境 `WEBAPP_ENABLE_SQL_IDE_V2=true`
2. 研发 + SA 账户试用全部 6 个 Feature
3. 观察 audit log 无异常、前端 console 无报错
4. 运维 dashboard：
   - Hazelcast `sqlIdeSchemas/Tables/Columns` 缓存命中率 > 50%
   - `query_execution_chunk` 表行数 / 占用增长正常
   - `sqlide_view_*` UNLOGGED 表 30min TTL 清理生效
5. 若 3 天无 P0/P1：进入阶段 1

### 阶段 1 — 部门（~50 人，第 8-21 天）
1. 挑选 1-2 个活跃数据分析团队，灰度账户开启
2. 每日站会收集反馈
3. 观察审计日志体量（export / page 增长 ≤ 预估 10x）
4. 性能指标达标（见 perf-report.md）
5. 连续 5 天无 P0/P1 缺陷进入阶段 2

### 阶段 2 — 全量（第 22 天起）
1. 全部环境 `WEBAPP_ENABLE_SQL_IDE_V2=true`
2. 老 `QueryWorkbenchPage` 保留至少 3 个版本（v2.2.3 / v2.3.0 / v2.4.0）作为兜底

## 观测指标

- 前端错误率（埋点 /分钟 / 用户）
- 后端 `/api/sql/v2/*` P99 延迟
- audit_log 行增速
- `query_execution_chunk` 表大小
- Feature flag 状态分布

## 升级路径

从阶段 0 → 1：修改 webapp 容器的环境变量，重建前端 bundle / hot-reload runtime-config.js 配置即可（见 vite.config.ts + docker-entrypoint.sh）。
