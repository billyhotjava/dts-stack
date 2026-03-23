# IT: Sprint-10 集成验证

本目录用于沉淀本 sprint 的集成验证结果与验收资产。

当前已覆盖：

- legacy / normal 模式下的 compose 启动恢复验证
- `restart: unless-stopped` / 一次性任务 `restart: "no"` 策略回归
- 关键后端健康检查与 `service_healthy` 依赖回归
- `dts-platform-webapp` 在后端延迟可用场景下的保持运行与自动切换验证

待补现场验收：
- 宿主机重启后的关键容器自恢复验证
- 代理入口与关键健康检查验证
