# 运维手册（Gate G4）

**功能**：治理 BI 创作、发布与导出闭环

**负责人**：DTS 平台/Analytics 运维
**风险等级**：中

## 1. 这是什么 / 正常状态

业务分析人员从已发布 QueryDataset 创建 Analysis，受治理查询后保存/发布，并在 Dashboard 配置定向联动；只有已发布且通过权限与密级判定的 Analysis 可导出 CSV/XLSX。

正常状态：`dts-analytics` healthy，平台页面返回 200；拖入字段后约 450ms 发起一次预览；单看板查询并发不超过 4；Analysis 查询/导出不超过 10000 行。

## 2. 健康检查

| 检查项 | 命令/端点 | 期望结果 |
|---|---|---|
| 容器状态 | `docker compose -f docker-compose-app.yml ps dts-analytics dts-platform-webapp` | Analytics `healthy`，webapp `running` |
| Analytics 功能健康 | `docker exec v223-dts-analytics-1 curl -fsS http://127.0.0.1:3000/api/health` | HTTP 2xx |
| Nginx 配置 | `docker exec v223-dts-platform-webapp-1 nginx -t` | `syntax is ok` / `test is successful` |
| 平台入口 | `curl -kfsS -o /dev/null -w '%{http_code}\n' https://dts.local/` | `200`（域名以实际 `.env` 为准） |
| 受保护 API | 未登录请求 `/bi/api/analysis/11` | `401/403`，不得匿名返回业务数据 |
| 页面功能 | `/bi/questions/{id}/edit` | 字段契约、图表、发布/导出按钮按生命周期和权限显示 |

## 3. 告警

| 告警 | 条件与阈值 | 级别 | 含义 | 处置 |
|---|---|---|---|---|
| BI API 失败率 | `/bi/api/analysis/**` 5 分钟内 5xx ≥5 次且失败率 ≥5% | P1 | 创建、查询、发布或导出受阻 | 按 5.1 用请求号定位；确认是否回滚 Analytics |
| BI 查询超时 | 10 分钟内 504 或 `ANALYSIS_QUERY_TIMEOUT` ≥3 次 | P2 | 仓库/预算/并发可能异常 | 按 5.2 检查上游、慢查询与预算，不直接放宽上限 |
| Analytics 不健康 | Compose health 连续 3 次失败（约 30 秒） | P1 | 所有 BI API 可能不可用 | 查看启动日志/PG 连通；必要时执行 release-plan 回滚 |
| 导出拒绝突增 | 10 分钟内导出 403/409 ≥10 次 | P2 | 权限、密级快照或发布状态异常 | 抽取请求号和 analysisId，对账权限/分类；禁止绕过封印 |

同一根因 15 分钟内按服务+错误码聚合抑制，避免逐请求告警风暴。

## 4. 关键日志与追踪

| 字段 | 用途 | 示例 |
|---|---|---|
| correlation/request id | 串联前端、Traefik、Analytics 查询 | `query-...` / `X-Request-Id` |
| analysisId / revisionId | 定位定义与发布版本 | `11` / `91` |
| datasetId / datasetVersion / checksum | 对账 pinned contract | UUID / `1` / checksum |
| actor subject | 权限与审计主体 | 账号 subject，不记录令牌 |
| classification snapshot | 解释导出允许/拒绝 | 快照 ID/密级枚举，不记录明细数据 |
| query duration / rowCount / cacheHit | 容量与慢查询判断 | `18ms` / `2` / `false` |

禁止记录：访问令牌、密码、导出文件正文、筛选中的个人敏感值、受控字段明文。审计记录与运行日志分离保留。

## 5. 故障处置

| 场景 | 症状 | 立即动作 | 恢复动作 | 可回滚 |
|---|---|---|---|---|
| QueryDataset/仓库不可用 | 预览/导出 409、5xx 或超时 | 保留请求号；确认 pinned version/checksum 和上游健康 | 恢复上游后用户显式重试；不得改写成其他数据集 | 是，代码可回滚；上游数据不可猜测修复 |
| 数据异常或契约漂移 | 列缺失、checksum conflict、图表空 | 停止发布；对账 published contract 与 Analysis spec | 从正确固定版本创建新草稿并重新校验 | 已发布 revision 不删除 |
| 查询卡住/重复 | 长时间 running、相同操作重复请求 | 前端取消旧查询；检查 correlation id 和并发水位 | 终止对应查询并重试一次；保持并发 4、limit 10000 | 是 |
| 导出被拒绝 | 403/409，按钮禁用或下载失败 | 核对 PUBLISHED、CARD/EXPORT、密级快照 | 修复权限/分类事实后重新请求，不直接放开端点 | 是 |
| 前端新包异常 | 白屏、console error、`/bi/api` 代理失败 | `nginx -t`、检查静态资源和容器日志 | 回滚 webapp 镜像；后端可保持新版本（API 增量兼容） | 是 |

## 6. 容量与扩容触发点

- 单次 Analysis 最大 10000 行；自动预览防抖 450ms，新请求取消旧请求。
- 单 Dashboard 最多 50 卡，浏览器同时查询最多 4 卡；陈旧 generation 结果不回写。
- 单 Analysis 最多 20 派生指标、50 筛选、10 排序。
- 客户真实并发和延迟基线尚待现场校准；在 15 分钟窗口内 P95 超过既定查询超时的 70% 或 5xx ≥5% 时先限流/定位，不直接提高预算。
- revision/audit 为增量数据；沿用平台既有保留策略，本 Sprint 不新增自动清理。

## 7. 禁止操作

- 不得绕过 `AnalysisQueryGateway` 直接拼 SQL 导出。
- 不得因 403/409 临时关闭权限或密级校验。
- 不得删除已发布 revision 来“回滚”。
- 不得在未确认镜像 ID 与健康状态前批量重启整套 Compose。
- 不得将本 Sprint 与工作区并行的数据建模改动一起构建 `dts-platform`。
