# Sprint-67 最终 Go/No-Go

**评审日期**：2026-07-20  
**Sprint 决策**：`GO`（新建模主线受控发布）

## 分项决策

| 决策项 | 结论 | 说明 |
|---|---|---|
| 新建模主线 | GO | BUSINESS_FIRST/ASSET_FIRST 真实 Chrome95 与 API/DB 记录闭环，不经过业务对象 |
| 旧写冻结 | GO | legacy write 返回 410，带退役与 successor 信息，不再新增旧对象 |
| 旧读兼容 | GO | 旧深链进入审计兼容/分类恢复，回滚只恢复读取能力，不恢复双写 |
| 专业模块交接 | GO | 同一 modelSpecId/revision 关联标准、指标、artifact、review、release、run、lineage |
| 权限与租户边界 | GO | Spring Security 403 和 PostgreSQL tenant-isolation 集成测试通过 |
| Chrome 95 | GO | 部署旅程 A/B/C 3/3，desktop/narrow；失败恢复定点 1/1 |
| 镜像回滚 | GO | 回滚/恢复各 58 秒，服务健康、关键数据库事实一致 |
| 旧表物理删除 | NO-GO | 当前退出门禁为 NO-DROP；继续保留只读结构与审计 |
| 外部 Airflow/dbt 提交成功 | NOT CLAIMED | 当前部署明确为 RUNTIME_DISABLED；只验收 run 记录和修复路径 |

## No-Go 条件复核

- 新模型不要求 `objectId`；
- 旧写 API 不产生新行；
- 自动映射、checksum、幂等和跨租户均有集成测试；
- 指标、发布、运行和血缘保留稳定后端记录；
- 只读角色不获得写入或迁移权限；
- Chrome 95 主线和回滚不恢复双写。

## 后续退出条件

物理删除只在旧调用与消费者归零、人工冲突处理完成、checksum 对账一致并取得备份/变更审批后另行执行。`RUNTIME_DISABLED` 环境解除后，外部运行器提交应作为部署环境验收项补证，但不改变本 Sprint 已完成的主线契约和受控发布结论。
