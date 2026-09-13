# 发布安全计划（Gate G3）

**变更类型**：Schema Expand + 跨服务 API + canonical BI UI + 后续独立 Contract 清理
**风险等级**：高
**当前 Gate**：GAP；代码与自动化门禁已补，真实 shadow/pilot、Chrome 95、四角色 E2E、旧镜像回切和数据库恢复演练尚未完成。

## 1. 两次发布，不混批

### R1：Governed BI Expand

- Platform 先提供已发布分析数据集内部契约与报表登记端点；Analytics 只持有数据集版本引用和运行快照。
- Analytics 增加 Analysis/Dashboard 版本发布、统一查询网关、审计和 outbox。
- `/bi/questions`、`/new`、`/:id`、`/:id/edit` 统一到 governed Analysis；创建从 `/bi/data` 已发布数据集开始。
- `analytics_revision` 使用线性窗口回填；新增列保持 nullable，并用 legacy-writer trigger 为旧镜像 INSERT 补 `version_no/status`，确保滚动部署与镜像回切不断写。
- `ANALYTICS_PUBLIC_SHARING_ENABLED` 在 app/dev/legacy Compose 中显式默认 `false`。老安装只有在确认仍需大屏公开链接后才显式设为 `true`。
- R1 不执行旧 BI DELETE/DROP，不移除 rollback 所需 handler。

### R2：Legacy BI Contract

- R1 通过完整纵向验收和回切演练后，另开发布窗口。
- 冻结旧写并记录 Card/Dashboard 最大 ID cutoff。
- 完整备份并恢复验证 `dts_analytics`。
- 执行 screen/template 全历史引用预检；任一 Card 引用都阻断。
- dry-run 通过后才允许 `backup_confirmed=true, apply=true` 执行清理。
- 路由 handler/schema 的物理删除晚于数据 Contract，避免同一窗口失去恢复手段。

## 2. 老安装升级兼容矩阵

| 场景 | 预期 | 保护机制 |
|---|---|---|
| 旧 DB + 新 Analytics | Liquibase 线性回填，启动时间不随 revision 数量平方增长 | `ROW_NUMBER() OVER` |
| 新 DB + 旧 Analytics 实例仍写 revision | INSERT 不因新列失败，且版本/状态可被新实例读取 | nullable Expand 列 + advisory-lock trigger |
| 新 DB + 旧镜像回切 | 旧 Card/Dashboard 仍可暂时读写 | R1 不执行 Contract DELETE/DROP |
| 老安装存在大屏历史 | screen/version/access/template/audit/assets/menu bindings 均不改 | 清理脚本无这些 DELETE target，引用命中 fail-closed |
| 老安装大屏引用旧 Card | R2 不执行 | `cardId` 四种命名扫描阻断 |
| 老安装需要大屏公开链接 | 运维显式开启并复验；默认安装不暴露匿名分享 | Compose 显式 default false |
| 仅有 DWD/DWS/ADS 历史 | 不做 BI 数据迁移；由 ModelSpec/dbt 选择性重建 | 数据建模发布流程拥有物化关系 |

## 3. 迁移与预检

R1 文件：

- `source/dts-analytics/src/main/resources/config/liquibase/changelog/0052_analysis_publication.xml`
- `source/dts-platform/src/main/resources/config/liquibase/changelog/20260817_04_bi_report_asset_registration.xml`
- `source/dts-admin/src/main/resources/config/liquibase/changelog/20260819-01_sprint94_analysis_audit_catalog.xml`

R1 上线前：

```sql
select model, model_id, count(*)
from analytics_revision
group by model, model_id
having count(*) > 2147483647;

select engine, asset_type, asset_key, count(*)
from bi_report_link
where asset_type is not null and asset_key is not null
group by engine, asset_type, asset_key
having count(*) > 1;
```

两条查询都必须返回 0 行。

R2 工具：

- `source/dts-analytics/src/main/resources/ops/sprint94_legacy_bi_cleanup.sql`
- 必填：`legacy_card_max_id`、`legacy_dashboard_max_id`
- 默认：`apply=false`
- apply 额外必填：`backup_confirmed=true`

## 4. R1 部署顺序

1. 记录 commit、Compose 参数、三个受影响容器 image ID/标签和当前大屏计数；给旧镜像加 rollback 标签。
2. 设置 `DTS_ANALYTICS_GOVERNED_BI_ENABLED=false`，保持 legacy write 可用；按安装实际需求设置 `ANALYTICS_PUBLIC_SHARING_ENABLED`，不得依赖隐式默认。
3. 先部署 `dts-platform`，验证内部数据集契约和报表登记的 service token 正/负向路径。
4. 再部署 `dts-analytics`，完成 revision/dashboard/outbox Expand；验证旧写 trigger、新 API flag-off、health 和审计 action catalog。
5. 部署 `dts-platform-webapp`；确认 `/bi/questions` 不再调用 `/api/card`，创建 CTA 指向 `/bi/data`。
6. Shadow 打开 governed flag，执行固定数据集→Analysis→Dashboard 对账。
7. Pilot 仅向指定部门/角色发布，观察至少 48 小时。
8. Chrome 95、A1～A4、审计/权限负向、公开分享边界和旧镜像回切全部通过后进入 Default。

只替换 `dts-platform`、`dts-analytics`、`dts-platform-webapp`（以及承载新审计 catalog 的 `dts-admin`）；不重建 PostgreSQL 数据容器。

## 5. R1 回滚

```bash
export DTS_ANALYTICS_GOVERNED_BI_ENABLED=false
docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-analytics
```

随后按发布前记录的 image 依次恢复 platform → analytics → webapp → admin（如 admin 已升级）。常规镜像回滚保留 Expand 列、trigger、outbox 和审计目录；旧镜像必须能继续写 revision。回滚后复验旧 API、大屏、公开链接策略和数据库指针。

## 6. R2 apply 与恢复

1. 停止 legacy 写入，归档 cutoff 和当前 screen 资产清单。
2. `pg_dump` 完整备份 `dts_analytics`，在隔离库恢复并验证 screen 全链。
3. 运行清理脚本 dry-run；输出必须显示 `ROLLBACK`，screen 引用为 false。
4. 独立审批后传入 `backup_confirmed=true -v apply=true`。
5. 对账 screen、versions、access、templates、audit、assets、menu/role binding，以及 governed Analysis/Dashboard。
6. 任何差异：停止服务，恢复完整 DB 备份和 R1 镜像。只恢复镜像不能恢复已删除 legacy 行。

## 7. 发布阻断条件

- screen/template 任一历史 payload 命中 Card 引用；
- cutoff 缺失、非法或冻结后仍有 legacy 写；
- 完整备份未验证可恢复；
- migration 预检异常或旧镜像写 revision 测试失败；
- service-token 精确路径正负向失败；
- registration backlog ≥20 持续 5 分钟，或 5 分钟服务端错误率 >5%；
- A4 越权负向失败、Chrome 95 未通过、公开分享策略未明确；
- R1 与 R2 被安排在同一不可回切窗口。

## 8. 尚待实证

- [ ] 预生产 Liquibase update → 旧镜像写 revision → 新镜像恢复演练。
- [ ] Chrome 95 与 A1～A4 真实纵向旅程。
- [ ] Shadow/pilot/default 的 image、commit、耗时和指标窗口。
- [ ] 大屏完整备份恢复与 R2 dry-run 归档。
- [ ] 48h pilot 观察。

证据未落盘前，G3 保持 GAP。
