> 状态: done-first-pass (2026-02-13)

# P0-04 操作审计（全链路可追溯）

## 目标
- 屏幕关键操作可按用户、时间、对象追溯。

## 范围
- BE：`analytics_screen_audit_log` + 写入链路。
- FE：操作记录抽屉与筛选。
- QA：操作-日志一致性核验。

## 交付物
- 审计表 + 查询 API
- 前端审计查看页

## 验收
- create/update/publish/rollback/share/delete 覆盖率 100%。
- 可按 screenId 拉通完整时间线。

## 回滚点
- 审计写入异常不阻塞主业务，进入补偿队列。
