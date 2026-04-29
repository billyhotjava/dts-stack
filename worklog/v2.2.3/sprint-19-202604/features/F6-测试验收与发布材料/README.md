# F6: 测试验收与发布材料

**优先级**: P1  
**状态**: READY  
**依赖**: F1, F2, F3, F4, F5

## 目标

为 Sprint-19 建立可执行发布门禁，避免 OpenMetadata 集成再次出现“代码存在但现场不可用”的状态。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | 自动化测试矩阵 | P1 | READY |
| T02 | Compose 冒烟与样例数据 | P1 | READY |
| T03 | 发布、升级与回滚说明 | P1 | READY |

## 完成标准

- [ ] Java 单测覆盖 FQN、parser、adapter、client 错误处理。
- [ ] compose 冒烟覆盖 OpenMetadata server、ingestion、platform、ingestion-service。
- [ ] 验收证据留存在 `it/README.md` 或其引用文件。
- [ ] 发布说明包含存量 `.env` 修复和回滚策略。
