# T03: 调整 depends_on 语义并明确边界

**优先级**: P1
**状态**: READY
**依赖**: T02

## 目标
将 Compose 中的依赖关系从“能启动”调整为“能就绪”，同时保留对老环境的兼容。

## 技术设计
- 对必须等待数据库、核心后端就绪的服务使用 `service_healthy`
- 对只需网络存在即可启动的服务保留 `service_started`
- 明确 webapp 与后端关系：Compose 只做最小门禁，真正稳态依赖由 entrypoint 自等待补齐

## 影响范围
- `docker-compose.yml`
- `docker-compose-app.yml`
- `docker-compose.legacy.yml`
- 设计与运维文档

## 验证
- [ ] 依赖关系调整后 compose 可正常启动
- [ ] 不引入循环依赖或冷启动死锁

## 完成标准
- [ ] depends_on 语义有明确理由
- [ ] 启动顺序更接近真实可用顺序
