# 发布安全计划（Gate G3）

**变更类型**：增量 API + 前端能力闭环
**风险等级**：中（新增受控导出端点，涉及权限与密级判定）

## 1. 迁移策略

| 阶段 | 内容 | 本次是否包含 | rollback 段 |
|---|---|---|---|
| Expand | 新增 `POST /bi/api/analysis/{id}/query/csv|xlsx`；Analysis DTO 新增可选 `permissions.export` | 是 | 回滚旧镜像即可；无数据迁移 |
| Migrate | 无 schema、无回填、无双写 | 否 | - |
| Contract | 不删除旧接口、字段或路由 | 否 | - |

## 2. 兼容性

| 消费方 | 证据 | 是否受影响 | 处置 |
|---|---|---|---|
| Analysis 编辑页 | `analysisApi.ts`、`AnalysisEditorPage.tsx` | 是，增量 | 旧 Analysis 数据的 `export` 缺失时前端默认禁用导出，fail-closed |
| Dashboard 编辑/预览 | `DashboardEditorPage.tsx`、dashcard settings | 是，增量 | 联动键写入既有 JSON settings；未配置卡片保持原行为 |
| 既有 Card 导出 | `CardResource`、`QueryExportService` | 仅共享 XLSX 修复 | CSV/JSON 路径不改；XLSX 小结果跟踪列后再自动列宽 |
| 外部调用方 | GitNexus API 路由扫描未发现既有 Analysis 导出消费方 | 否 | 新端点不替换旧端点 |
| dts-platform | 无 Sprint-95 owned 代码变更 | 否 | 不随本 Sprint 构建，避免吸收并行建模改动 |

## 3. 数据与不可逆边界

- 本 Sprint 不修改数据库结构，不做数据回填。
- 发布 Analysis/Dashboard revision 是用户显式业务动作；代码回滚不会删除已经发布的 revision。
- 导出文件离开平台后不可召回，因此导出前必须同时满足：Analysis 已发布、CARD/EXPORT 权限、密级快照允许。

## 4. 部署与回滚

部署顺序：

1. 将当前 `dts-analytics:1.0.0`、`dts-platform-webapp:1.0.0` 分别标记为 `rollback-sprint95-20260820`。
2. 先构建并重建 `dts-analytics`，确认容器 healthy、`/api/health` 正常。
3. 再构建并重建 `dts-platform-webapp`，确认 `nginx -t`、HTTPS 页面和 `/bi/api` 代理正常。
4. 执行一次 Sprint-95 聚焦浏览器旅程；失败立即停止，不重建 `dts-platform`。

回滚命令（在仓库根目录执行）：

```bash
docker tag dts-analytics:rollback-sprint95-20260820 dts-analytics:1.0.0
docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-analytics
docker tag dts-platform-webapp:rollback-sprint95-20260820 dts-platform-webapp:1.0.0
docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-platform-webapp
```

回滚后校验：`docker compose -f docker-compose-app.yml ps`、Analytics health、webapp `nginx -t`、平台 HTTPS 200、最近 10 分钟容器日志无启动错误。

**回滚准备结果（2026-08-20）**：部署前旧镜像已标记为 `rollback-sprint95-20260820`；新镜像已按后端→前端顺序部署并通过健康检查。为避免对当前用户造成第二次主动中断，本次未切回旧镜像做生产回滚演练，因此 G3 保留 GAP，不记完整 PASS。

## 5. 影响面与加固

- 影响模块：`dts-analytics`、`dts-platform-webapp`；不涉及 `dts-platform` schema 或代码。
- 权限/密级是高敏感子路径：JUnit 覆盖成功 CSV/XLSX、无权限 403、密级拒绝 403；PlatformPermissionFilter 契约测试覆盖 CARD/EXPORT 映射。
- 查询仍经过 `AnalysisQueryGateway`，保留 pinned dataset contract、RLS、预算、审计和 10000 行上限。
