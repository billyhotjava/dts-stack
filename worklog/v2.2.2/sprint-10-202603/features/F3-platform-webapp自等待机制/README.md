# F3: platform-webapp 自等待机制

**优先级**: P0
**状态**: IN_PROGRESS

## 目标
让 `dts-platform-webapp` 在后端未就绪时保持运行并持续等待，而不是直接退出。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 复盘 platform-webapp 当前启动失败链路 | P0 | DONE | - |
| T02 | 设计 entrypoint 自等待与 Nginx 配置切换机制 | P0 | IN_PROGRESS | T01 |
| T03 | 增加 platform-webapp 自等待回归测试 | P1 | READY | T02 |

## 完成标准
- [ ] `dts-platform-webapp` 不再因 upstream 暂时不可解析而退出
- [ ] 容器保持运行态并在后端就绪后自动切换到正式代理配置
- [ ] 行为被测试覆盖
