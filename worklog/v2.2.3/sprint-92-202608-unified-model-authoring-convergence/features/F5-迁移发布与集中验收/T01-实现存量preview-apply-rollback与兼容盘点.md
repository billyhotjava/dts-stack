# T01：实现存量 preview/apply/rollback 与兼容盘点

**优先级**：P0  
**状态**：IMPLEMENTED  
**依赖**：F4/T02、F0/T01

## 目标

对存量 draft/provenance/projection 做可预览、可分批、可回滚补齐，并取得旧 API 消费分母；不自动修改 ModelSpec ownership/status。

## 技术设计（Contract-first）

- **输入契约**：domain profile 当前分布；draft source bundle/pins/status；旧路由访问日志；release-plan §3。
- **输出契约**：preview report `{counts,rows:[id,status,beforeChecksum,provenance,coverage,action,reason]}`；apply/rollback `{batchId,applied,skipped,conflicts}`。
- **数据流**：只读 scan → deterministic classify → 人工确认 → cursor batch ≤100 → compare-and-set update → batch evidence。
- **错误路径**：pins/before checksum 漂移、缺 bundle、过期、COMMITTED 不可证明字段全部 skip/unresolved；rollback 遇后续修改拒绝。
- **复用点**：既有 migration/script conventions、projection adapter、repository CAS；禁止 Python/SQL 直接无围栏批量改状态。
- **兼容盘点**：旧 route 按 caller/window/count/lastSeen 记录；UNKNOWN 保持 UNKNOWN，不能推断 0。

## 影响范围

受控 migration command/report、draft repository 的 CAS 补齐方法、运维证据和旧 API 观测；不修改 ModelSpec ownership/status，不删除任何路由或列。

## 验证（RED→GREEN）

- [ ] 受控副本含正常/漂移/过期/COMMITTED/缺 bundle 五类。
- [ ] preview 重复运行确定；apply 重放幂等；rollback/re-apply 可执行。
- [ ] 报告和日志无 SQL/YAML 正文。

## Definition of Done

- [ ] OQ-03 有实测调用分母或明确 UNKNOWN/观测期。
- [ ] 存量补齐不复活草稿、不改 receipt、不覆盖用户修改。
- [ ] Contract 删除范围仍不进入本 Sprint。
