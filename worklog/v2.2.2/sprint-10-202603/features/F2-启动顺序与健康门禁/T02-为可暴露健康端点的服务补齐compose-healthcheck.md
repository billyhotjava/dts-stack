# T02: 为可暴露健康端点的服务补齐 compose healthcheck

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
让关键后端服务在 compose 层具备基础健康门禁，而不是仅靠进程启动。

## 技术设计
- 对 admin、platform、ingestion、analytics 采用 `curl http://127.0.0.1:<port>/management/health`
- 与 legacy/normal compose 保持一致
- 避免过于严格导致冷启动误判

## 影响范围
- `docker-compose.yml`
- `docker-compose-app.yml`
- `docker-compose.legacy.yml`
- 相关测试

## 验证
- [ ] Compose healthcheck 配置通过
- [ ] 回归测试覆盖关键健康检查

## 完成标准
- [ ] 关键服务健康检查可执行
- [ ] 误判率可控
