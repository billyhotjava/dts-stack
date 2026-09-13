# T01：原子校验提交 ModelSpec、bundle 与依赖 pins

**优先级**：P0  
**状态**：IMPLEMENTED  
**依赖**：F1/T03、F2/T03

## 目标

在一个事务和一个幂等 command 中校验并提交 ModelSpec snapshot、实现 bundle、projection 与 dependency snapshot，返回唯一可供候选消费的 pins。

## 技术设计（Contract-first）

- **输入契约**：validate `{expectedEtag}`；commit `{expectedEtag,validatedChecksum,dependencyChecksum,idempotencyKey}`；draft 内 model/files/projection/base pins。
- **输出契约**：validation 四组诊断；commit `{modelRevision/checksum,implementationId/revision/checksum,dependencyChecksum,artifactCount,etag}`。
- **数据流**：锁 draft/model/implementation → validate ModelSpec → static dbt validate → projection/rewrite fence → dependency resolver → freeze/import → CAS model revision → implementation/artifact/receipt/audit → COMMITTED。
- **错误路径**：400 请求；403 权限；409 status/idempotency/dependency；412 ETag/pins；422 contract/diagnostic；500 任一写点失败全回滚。
- **复用点**：`DbtImplementationDraftService.commit`、ModelSpec validator/repository、bundle manifest、artifact importer、dependency resolver。
- **事务围栏**：禁止 resource 或前端分步补状态；`COMMITTING` 仅存在事务内/可恢复持久状态，按 runbook 对账。

## 影响范围

authoring resource/service、既有 draft commit 编排、ModelSpec/implementation/artifact repositories、dependency resolver、审计 recorder 和 Testcontainers；不修改 candidate 状态机。

## 验证（RED→GREEN）

- [ ] MockMvc 精确 DTO/错误码测试。
- [ ] Testcontainers 在每个写点失败注入，断言 model/implementation/artifact/receipt/audit 无半提交。
- [ ] 两 actor 并发、幂等 replay、异 payload、dts-dbt 故障。

## Definition of Done

- [ ] commit 回执 pins 可直接创建候选。
- [ ] 失败/冲突可重试且不覆盖他人修改。
- [ ] 日志/审计无 SQL/YAML 正文。
