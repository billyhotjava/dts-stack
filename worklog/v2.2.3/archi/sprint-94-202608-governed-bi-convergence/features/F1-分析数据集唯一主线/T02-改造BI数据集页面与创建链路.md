# T02：改造 BI 数据集页面与创建链路

**优先级**：P0
**状态**：DRAFT
**依赖**：T01

## 用户可测试目标

分析维护者进入 `/bi/data` 只看到有权消费的已发布 DWS/ADS Query Dataset；查看契约后点击“创建分析”，canonical editor 收到并展示同一 `datasetId/version/checksum`，不再产生 `dbId`、Analytics Database 或裸数据源分叉。

## UI 规格

- 筛选：关键词、业务数据域、owner 部门、DWS/ADS、密级；状态写入 URL，刷新可恢复。
- 列表：名称/说明、版本、分层、owner、刷新策略、密级、语义模型、更新时间和创建 capability。
- 契约 drawer：维度/指标/类型/时间粒度/策略摘要/checksum；永不展示 SQL。
- 四态：skeleton；无 published 数据集的引导；筛选空态；error+重试/correlationId；success+分页。
- “创建分析”：仅有 dataset read + analysis write 且 eligible 时启用；DWD 未授权解释原因，ODS/STG 不出现。
- 导航统一 `/bi/questions/new?datasetId=...&version=...&checksum=...`（ADR-94-13 已钉死，见 F1 README「Route contract」）；旧 `dbId/vds/base` 参数仅兼容解析，不用于新链接。
- 分页遵循前端既有约定：UI 默认每页 10 条，切换条数重新拉取并重置第 1 页。

## 实现边界

- 替换 `DataPage.tsx`（391 行）当前双源加载；删除页面对 Analytics Database 的新建依赖，不删除后端兼容 API。
- **`/bi/data/:dbId` 及其两条下钻子路由在本 Sprint 保持可直达**（`route-inventory.md` #3～#5 标记为"冻结"）。移除入口链接后它们成为孤儿路由，属预期状态，不在本 Sprint 删除或重定向。
- 在拆分后的 analysis client 中新增 typed published-dataset 调用；`analyticsApi.ts` 不得继续增长。
- 使用后端 permissions/capabilities 控制按钮，不在页面硬编码角色名。
- 旧 bookmark 保持当前直达与兼容分流行为，可显示解释提示；本 Sprint 不新增 redirect、菜单或 route owner。

## RED → GREEN

1. RED source-contract：页面仍调用 databases/platform datasource，创建 URL 含 `dbId`（现状：`DataPage.tsx:256` 生成 `/bi/questions/new?dbId=`）。
2. GREEN component/route contract：只调用 published projection，URL 精确为 `/bi/questions/new?datasetId=&version=&checksum=`。
3. GREEN UI test：loading/两类 empty/error/success；403 不伪装空态；drawer 无 SQL。
4. GREEN Chrome95：筛选、分页、drawer、键盘、导航、Network/console 通过。

## 影响范围与先决 impact

预计文件：`DataPage.tsx`、BI route adapter、typed API client、i18n、聚焦测试。编辑实际函数/组件前运行 fresh impact；若 route resolver 影响 HIGH，只切 canonical producer，并通过现有兼容分流保持旧路由行为，不新增 redirect。

## Definition of Done

- [ ] 页面不再请求/显示 Analytics Database 和裸数据源。
- [ ] 创建链参数与 F2 POST payload 一致，IT-03 证明无断链。
- [ ] 四态、权限、URL persistence、Chrome 95 和文案验收通过。
- [ ] package.json 无新增依赖，超大 API client 已抽取而非增长。
- [ ] 旧链接有兼容行为且无第二个编辑器主线。
