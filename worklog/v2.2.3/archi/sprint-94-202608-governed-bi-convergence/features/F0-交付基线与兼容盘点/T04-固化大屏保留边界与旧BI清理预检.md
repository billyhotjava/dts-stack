# T04：固化大屏保留边界与旧 BI 清理预检

**优先级**：P0
**状态**：IN_PROGRESS
**依赖**：T01；目标安装只读数据库访问

## 可测试目标

把“仅大屏历史必须无损保留”落实为机器可执行门禁：完整列出 screen durable set，扫描当前/历史 screen 与模板中的所有 Card 引用，并提供带精确 cutoff、备份确认、默认 ROLLBACK 的 legacy BI 清理脚本。

## 输入、输出与安全契约

| 项目 | 契约 |
|---|---|
| 保留输入 | screen、version、access、template/version、audit、policy、lock、assets、菜单/角色绑定 |
| 可清理输入 | cutoff 以内的 legacy Card/Dashboard，以及关联 dashcard/alert/link/bookmark/activity/revision/query trace/pulse |
| 输出 | `sprint94_legacy_bi_cleanup.sql`、dry-run 记录、Card 引用命中清单、发布 cutoff |
| 默认行为 | `apply=false`；任何 screen 引用或未确认备份都 ROLLBACK 并退出非零 |
| 禁止 | 自动 DROP DWD/DWS/ADS、删除 ODS/source/ModelSpec/dbt project、近似转换 MBQL |

## RED → GREEN

1. RED：旧清理边界没有 executable guard，screen template/history 可能漏扫。
2. GREEN contract：脚本扫描 `cardId/card_id/sourceCardId/source_card_id`，cutoff 必填，apply 需 `backup_confirmed=true`。
3. GREEN dry-run：在当前库输出 legacy target 和 screen preserved 计数并显式 `ROLLBACK`。
4. GREEN target preflight：对每个将升级安装重复运行；命中引用则保持 BLOCKED。

## Definition of Done

- [x] 清理脚本默认 dry-run，当前本地库执行后无数据变化。
- [x] screen、screen version、template、template version 均纳入引用扫描。
- [x] cutoff 与备份确认是 apply 硬门禁，清理范围不使用无界当前最大 ID。
- [ ] 目标安装完整备份已验证可恢复，screen durable set 有升级前快照。
- [ ] 目标安装引用扫描为零；非零时已给出具体大屏/版本/模板 owner，不得放行。
- [ ] 数据建模 owner 另行确认 DWD/DWS/ADS 重建清单；Analytics 脚本不跨域 DROP。
