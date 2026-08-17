# 发布安全计划（Gate G3）

**变更类型**：Schema Expand + 跨服务 API + 受管发布能力  
**风险等级**：高（涉及受众、密级、跨服务登记和历史兼容）  
**当前 Gate**：GAP；计划与回切路径已具备，真实 shadow/pilot、数据库 rollback 演练和 48h/7d 观察尚未执行。

## 1. 发布边界

本次只交付治理型 BI 主线，不执行 Metabase 退役 S1～S4：

- 新增分析/看板的校验、版本、发布和受众登记；旧 `/api/card`、旧 dashboard 读写和既有路由继续工作。
- Analytics 只保存平台已发布数据集版本的引用和不可变快照，不成为数据集治理 owner。
- Platform `bi_report_link` 是受众事实源；Analytics 通过幂等 registration + outbox/reconcile 登记。
- `DTS_ANALYTICS_GOVERNED_BI_ENABLED` 是本次紧急回切开关。
- `DTS_ANALYTICS_LEGACY_CARD_WRITE_ENABLED` 固定保持 `true`，本 Sprint 只暴露状态与调用指标，禁止借本次发布关闭旧写。
- 本次没有 DROP、rename、旧数据迁移 apply、路由重定向或旧入口删除。

## 2. 迁移策略

| 阶段 | 内容 | 本次是否包含 | 回滚策略 |
|---|---|---:|---|
| Expand | Analytics revision/dashboard 字段、registration outbox；Platform report asset identity 字段/索引 | 是 | changelog 有 rollback；生产应用回滚默认保留 Expand 数据 |
| Migrate | 旧 revision 只回填顺序版本和 `DRAFT`；旧 report link 保持无 asset identity | 仅兼容回填 | 不做批量资产转换，不存在迁移 batch |
| Contract | 删除旧字段、旧表、旧路由、关闭旧写 | 否 | 必须在后续 S1～S4 独立审批 |

迁移文件：

- `source/dts-analytics/src/main/resources/config/liquibase/changelog/0052_analysis_publication.xml`
- `source/dts-platform/src/main/resources/config/liquibase/changelog/20260817_04_bi_report_asset_registration.xml`

上线前只读预检：

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

两个查询都应返回 0 行。首个查询用于确认 `version_no int` 的回填上限；第二个查询用于确认 partial unique index 可安全建立。

## 3. 兼容性与消费方

| 消费方 | 契约变化 | 处置 |
|---|---|---|
| dts-platform-webapp 分析/仪表板 | 新增 validate/publish/versions/retry API 与可选响应字段 | 后端先部署；旧前端不读取新字段仍可运行 |
| dts-platform `bi_report_link` | 新增 nullable asset identity/version 和非空 reconcile status | 旧手工链接不回填 identity；旧 CRUD DTO 只增可选字段 |
| dts-analytics 旧 Card/Dashboard | 不改既有路由与写入语义 | legacy write flag 保持 on；`analytics.bi.legacy.calls` 建立观测分母 |
| BI 消费者 | 受管仪表板仅在 registration=`AVAILABLE` 后可消费 | pending/failed 不降级为公开旧链接，不绕过受众过滤 |
| 运维监控 | 新增 Micrometer 指标和 health component | 先接入 dashboard，再进入 pilot |

## 4. 部署顺序

1. 记录当前 commit、Compose 参数和三个受影响容器的 image ID/标签；给旧镜像添加本次 rollback 标签。
2. 设置 `DTS_ANALYTICS_GOVERNED_BI_ENABLED=false`、`DTS_ANALYTICS_LEGACY_CARD_WRITE_ENABLED=true`，执行 Compose config 校验。
3. 先部署 `dts-platform`，完成 `bi_report_link` Expand 并验证旧 report API。
4. 再部署 `dts-analytics`，完成 revision/dashboard/outbox Expand，验证 health、旧 card/dashboard 和新 API 在 flag off 时返回 `GOVERNED_BI_DISABLED`。
5. 部署 `dts-platform-webapp`；flag 仍关闭时旧主线保持可用。
6. Shadow：在非用户流量的验收实例将 governed flag 打开，执行固定数据集/分析/看板对账；用户结果不切换。
7. Pilot：生产打开 governed flag，但只发布给指定试点部门/角色；观察至少 48 小时。
8. Default：指标符合阈值且回滚演练通过后，允许所有授权部门使用；旧读旧写继续开启并观察 7 天。

只重建/重建后替换 `dts-platform`、`dts-analytics`、`dts-platform-webapp`，不连带重建数据库或其他服务。

## 5. 回滚

### 5.1 首选：能力回切

```bash
export DTS_ANALYTICS_GOVERNED_BI_ENABLED=false
export DTS_ANALYTICS_LEGACY_CARD_WRITE_ENABLED=true
docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-analytics
```

期望：新 `/api/analysis/**` 与 dashboard 发布动作返回 503/`GOVERNED_BI_DISABLED`；旧 Card/Dashboard 读写仍可用；已产生的 revision/outbox/asset identity 保留。

### 5.2 镜像回滚

1. 将三个 image 变量恢复到发布前记录值。
2. 按 `dts-platform` → `dts-analytics` → `dts-platform-webapp` 顺序，用 `--no-deps --force-recreate` 替换。
3. 不执行 Liquibase rollback，不删除 Expand 列/表；旧二进制必须容忍这些附加结构。
4. 分别验证容器健康、HTTP、旧 Card/Dashboard、已发布旧入口和数据库指针。

### 5.3 不可逆/需人工判断部分

- 已登记到 Platform 的新 asset identity 不随应用回切删除；它在受众过滤下可禁用，禁止直接物理删除。
- 已发布 revision 和 outbox 证据保留；如登记失败，优先前向重试，不手工改 `AVAILABLE`。
- 只有预生产数据库可演练 changelog rollback；生产常规回滚保留 Expand 数据，避免丢失发布证据。

## 6. Gate 与职责

- 维护者：准备草稿和依赖，不批准自己的生产发布。
- 独立发布者：确认受众、密级、有效期和 checksum 后发布。
- Release operator：执行 flag/image 切换与回滚；不修改业务定义。
- Analytics/Platform on-call：观察指标并按 runbook 处置。

发布阻断条件：任一 migration 预检异常、legacy write flag 不为 true、registration backlog ≥20 持续 5 分钟、5 分钟 server error ratio >5%、真实角色负向越权失败、Chrome 95 未通过。

## 7. 尚待实证

- [ ] 预生产 Liquibase update → rollback → update 演练。
- [ ] Shadow/pilot/default 的实际 image、commit、耗时和指标窗口。
- [ ] 紧急 flag 回切和旧镜像恢复演练。
- [ ] 48h pilot 与 7d default 观察。

在以上证据落盘前，G3 保持 GAP，不得标记 PASS。
