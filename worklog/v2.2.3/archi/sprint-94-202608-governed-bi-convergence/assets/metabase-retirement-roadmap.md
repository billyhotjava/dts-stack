# Metabase 仿造功能退役路线

**决策日期**：2026-08-19
**决策**：旧 Card/Dashboard/Collection/Model/Trash/Pulse/Subscription/MBQL/VDS 不再作为迁移对象。老安装只无损保留大屏历史链；DWD/DWS/ADS 由数据建模的 ModelSpec/dbt 重新生成。

这项决策取代 2026-08-17 的“未知调用量阻断退役”假设。调用量仍可用于运维观察，但不决定数据保留。真正的硬门禁是大屏引用、旧写 cutoff、完整数据库备份、恢复演练和 Expand/Contract 分离。

## 1. 永久保留与可清理边界

| 类别 | 处置 | 说明 |
|---|---|---|
| `analytics_screen` | 永久保留 | 当前大屏定义、背景、变量、页面、轮播、V2 spec 与密级快照 |
| `analytics_screen_version` | 永久保留 | 历史发布版本和回退依据 |
| screen access/lock/policy/audit | 永久保留 | 权限、协作、合规与审计证据 |
| screen template/template version | 永久保留 | 模板及其历史快照 |
| 大屏素材、菜单、角色绑定 | 永久保留 | 防止升级后入口、图片或授权断层；菜单只允许原位更新，不换稳定身份 |
| legacy Card/Dashboard 及关联 ACL/link/revision/query trace | 可清理 | 不迁移为 Analysis；必须先证明大屏 payload 无 Card 引用 |
| Collection/Model/Trash/Pulse/Subscription/旧 VDS/MBQL | 可清理 | 不再复制 Metabase 产品面，不承担历史兼容义务 |
| DWD/DWS/ADS 物化表 | 可重建 | 由 ModelSpec、dbt 项目、发布记录和源数据重建；不得把物化表误当治理事实源 |
| ODS/source、ModelSpec、dbt 项目/版本 | 必须保留 | 它们是重建 DWD/DWS/ADS 的来源，不属于旧 BI 清理范围 |

## 2. 发布阶段

| 阶段 | 承接 | 允许动作 | 退出门禁 |
|---|---|---|---|
| **E0 Expand** | Sprint-94 | governed Analysis/Dashboard 主线；`/bi/questions` canonical 收敛；schema Expand；旧写兼容触发器；清理 dry-run；公开分享显式默认 false | 聚焦测试、构建、三角色 E2E、旧镜像回切、大屏对账全部通过 |
| **C1 Contract apply** | F6/T03 独立发布 | 冻结 legacy 写入；归档 Card/Dashboard cutoff；运行 screen 引用预检；执行有备份确认的 legacy 数据 DELETE；关闭/重定向旧入口 | 大屏及版本/权限/模板/审计计数和 checksum 不变；新主线可用；恢复演练通过 |
| **C2 Physical removal** | C1 稳定后的独立变更 | 删除 legacy route handler、无引用 service/page、最终旧 schema | 至少跨过约定回滚窗口；旧镜像不再是回滚方案；数据库备份仍可恢复 |

**当前位置**：E0 实现收口，真实 E2E/灰度/回滚证据仍待完成。C1 不与 E0 同批执行。

## 3. C1 硬门禁

1. 停止所有 legacy Card/Dashboard 写入，记录 `legacy_card_max_id` 与 `legacy_dashboard_max_id`；cutoff 之后出现 legacy 行即重新冻结。
2. 对整个 Analytics 数据库执行 `pg_dump`，验证文件非空并在隔离库完成恢复演练。
3. 执行 `sprint94_legacy_bi_cleanup.sql` 的 dry-run；脚本默认 `apply=false` 并以 `ROLLBACK` 结束。
4. 扫描 screen、screen version、template、template version 的 `cardId|card_id|sourceCardId|source_card_id`。任一命中都必须阻断，禁止靠人工忽略。
5. 只删除 cutoff 以内的 legacy Card/Dashboard 及其关联数据；不得按“当前最大 ID”无界删除，以免误删 governed 新行。
6. apply 必须显式传入 `backup_confirmed=true`；未确认备份时脚本终止并回滚。
7. 清理后逐项对账大屏、版本、权限、模板、审计、素材和菜单/角色绑定；任何差异都触发恢复。

## 4. 大屏仍含 Card 引用时

- 不删除被引用 Card，也不执行部分“猜测等价”的 MBQL 转换。
- 先明确该大屏是否应冻结现有渲染结果，或由业务维护者用已发布 Analysis revision 重建组件。
- 迁移后重新扫描所有历史版本和模板；不能只看当前 screen 行。
- 引用未清零时，C1 整体保持 BLOCKED。

## 5. 数仓重建边界

- Analytics 清理脚本不直接 DROP `biadmin` 的 DWD/DWS/ADS，避免用跨域硬编码替代数据建模发布流程。
- 需要重建时，由数据建模对目标 ModelSpec/dbt release 执行有选择的 build/test/materialize，并校验关系、行数、主键/粒度和下游契约。
- ODS/source、ModelSpec、dbt project、profiles、发布记录和治理映射必须先备份并保留。
- 删除旧物化关系和创建新关系属于同一次“数仓发布”，与 Analytics legacy BI Contract 分开留证。

## 6. 回滚语义

- E0 回滚：关闭 `DTS_ANALYTICS_GOVERNED_BI_ENABLED`，恢复旧镜像；Expand 列和触发器保留，旧写入方仍可产生有效 revision 默认值。
- C1 apply 前回滚：无需动作，dry-run 已回滚。
- C1 apply 后回滚：恢复完整数据库备份和旧镜像；不能仅回滚镜像，因为 legacy 数据已删除。
- C2 后回滚：只依赖数据库备份/发布包，不再承诺旧 handler 可即时回切。
