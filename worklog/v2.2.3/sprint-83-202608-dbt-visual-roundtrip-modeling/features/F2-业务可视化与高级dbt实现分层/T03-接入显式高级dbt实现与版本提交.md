# T03：接入显式高级 dbt 实现与版本提交

**优先级**：P1  
**状态**：DRAFT  
**依赖**：T01～T02

## 目标

只在现有模型详情“数据实现”阶段为技术维护者显式开放 DBT_MANAGED 实现；SQL 修改进入租户/计划/模型隔离草稿，validate 无副作用，commit 通过 CAS 后创建新的 immutable Implementation Revision。普通业务可视化不挂载本编辑器。

## Contract-first

- **创建草稿**：body=`baseModelRevision/baseImplementationRevision/baseChecksum/idempotencyKey`；返回 draftId/ETag/expiresAt。
- **入口门禁**：模型 ownership、read/write 与建模维护者 authority 均满足；普通可视化与高级实现互斥显示。
- **保存源文件**：路径经过 project-root 归一化；内容/文件数/大小受 NFR 限制。
- **validate**：只 parse/compile，返回 diagnostics、proposed structure、artifact checksum；不得 build/run。
- **commit**：body=`draftId/expectedETag/validatedChecksum/idempotencyKey`；输出新的 implementationRevision/checksum。
- **错误路径**：过期 410、CAS 409/412、非法路径 400、unsupported BLOCKED 422、越权 403。

## 验证

- [ ] validate 后业务表、共享 projectDir、candidate 均不变化。
- [ ] commit 重放幂等；并发只一个 winner。
- [ ] 审计含 checkpoint/commit，不含 SQL 正文。
- [ ] 不存在独立菜单、模型清单或旧 `/studio/sql-modeling` 写入口。

## Definition of Done

- [ ] 高级 dbt 编辑不会绕过 ModelLifecycle 或直接发布，也不会把 SQL 暴露给普通业务可视化。
