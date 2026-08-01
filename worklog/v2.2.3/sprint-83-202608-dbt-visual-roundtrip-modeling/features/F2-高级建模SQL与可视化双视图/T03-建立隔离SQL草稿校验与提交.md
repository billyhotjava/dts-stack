# T03：建立隔离 SQL 草稿、校验与实施版本提交

**优先级**：P0  
**状态**：DRAFT  
**依赖**：T01～T02

## 目标

DBT_MANAGED 的 SQL 修改先进入租户/计划/模型隔离草稿；validate 无副作用，commit 通过 CAS 后创建新的 immutable Implementation Revision。

## Contract-first

- **创建草稿**：body=`baseModelRevision/baseImplementationRevision/baseChecksum/idempotencyKey`；返回 draftId/ETag/expiresAt。
- **保存源文件**：路径经过 project-root 归一化；内容/文件数/大小受 NFR 限制。
- **validate**：只 parse/compile，返回 diagnostics、proposed structure、artifact checksum；不得 build/run。
- **commit**：body=`draftId/expectedETag/validatedChecksum/idempotencyKey`；输出新的 implementationRevision/checksum。
- **错误路径**：过期 410、CAS 409/412、非法路径 400、unsupported BLOCKED 422、越权 403。

## 验证

- [ ] validate 后业务表、共享 projectDir、candidate 均不变化。
- [ ] commit 重放幂等；并发只一个 winner。
- [ ] 审计含 checkpoint/commit，不含 SQL 正文。

## Definition of Done

- [ ] SQL 编辑不会绕过 ModelLifecycle 或直接发布。
