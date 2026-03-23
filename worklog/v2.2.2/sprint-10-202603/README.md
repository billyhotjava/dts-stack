# Sprint-10: 容器自恢复与启动编排稳态化

**时间**: 2026-03
**状态**: IN_PROGRESS
**目标**: 解决服务器重启后容器不能自动恢复、前端容器依赖后端启动顺序脆弱的问题，尤其收敛 `dts-platform-webapp` 需要人工二次启动的现场故障。

## 背景

当前 `legacy` 现场已经具备平滑升级能力，但运行稳态仍有明显短板：

- 宿主机重启后，并非所有关键容器都会自动恢复
- `dts-platform-webapp` 在后端服务尚未可解析/可访问时会直接退出
- 现有 compose 里的 `depends_on` 大量使用 `service_started`，只能表达“进程启动”，不能表达“服务就绪”
- 现场运维仍可能需要人工二次 `up -d dts-platform-webapp`，不符合交付要求

因此该工作从“升级编排”切换为“运行时自恢复与启动编排稳态化”，单独立为 `Sprint-10`。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 容器自恢复策略 | 3 | IN_PROGRESS |
| F2 | 启动顺序与健康门禁 | 3 | READY |
| F3 | platform-webapp 自等待机制 | 3 | IN_PROGRESS |
| F4 | 重启回归与现场验收 | 3 | READY |

## 完成标准
- [ ] 宿主机重启后，关键运行容器可自动恢复
- [ ] `legacy` 与 `normal` 模式下的重启策略一致且可验证
- [ ] `dts-platform-webapp` 不再因后端暂时未就绪而退出
- [ ] Compose 依赖关系清晰区分 `service_started` 与 `service_healthy`
- [ ] 补齐自动化回归与现场验收文档
