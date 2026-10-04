# F1：发布候选与证据真值

**优先级**：P0
**状态**：IN_PROGRESS

## 目标

建立计划级发布候选、候选条目、状态机和读模型，使第六步围绕明确的发布范围运行，并让每条证据绑定不可歧义的模型版本。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 冻结第六步输入输出与完成语义 | P0 | DONE | - |
| T02 | 建立 ReleaseCandidate 及 Entry 持久化契约 | P0 | IN_PROGRESS | T01 |
| T03 | 实现候选范围 CAS 幂等和状态机 | P0 | IN_PROGRESS | T02 |
| T04 | 建立候选读模型与 API | P0 | IN_PROGRESS | T03 |

## 完成标准

- [x] 候选范围显式列出 `modelSpecId/revision/checksum`，不复制 ModelSpec 正文。
- [x] 候选状态只能按权威状态机迁移，非法迁移、重复请求和并发覆盖稳定失败。
- [x] 模型 revision 漂移后可通过服务端 refresh 原子进入 STALE，后续动作 fail closed。
- [x] 计划级 API 一次返回候选摘要、条目、证据槽位、阻塞原因和允许动作。

> F1 代码与测试源码已完成，运行验证按 Sprint 统一测试窗口延后；因此 Feature 保持 IN_PROGRESS。
