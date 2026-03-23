# F2: 启动顺序与健康门禁

**优先级**: P0
**状态**: READY

## 目标
收紧关键后端服务的健康检查与依赖表达，减少“进程已启动但服务尚不可用”导致的前端/上游失败。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 盘点 admin/platform/ingestion/analytics 当前健康检查能力 | P0 | READY | - |
| T02 | 为可暴露健康端点的服务补齐 compose healthcheck | P0 | READY | T01 |
| T03 | 调整 depends_on 语义并明确 service_started 与 service_healthy 的边界 | P1 | READY | T02 |

## 完成标准
- [ ] 关键后端服务有可执行健康检查
- [ ] Compose 依赖语义与真实就绪条件一致
- [ ] 启动顺序问题被压缩到最小
