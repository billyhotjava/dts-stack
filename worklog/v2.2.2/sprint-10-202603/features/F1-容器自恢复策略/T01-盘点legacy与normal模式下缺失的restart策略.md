# T01: 盘点 legacy 与 normal 模式下缺失的 restart 策略

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
梳理当前 compose 中哪些长期运行服务缺失 `restart`，哪些一次性任务不应自动重启。

## 技术设计
- 对比 `docker-compose.yml`、`docker-compose-app.yml`、`docker-compose.legacy.yml`
- 标记长期运行服务、前端容器、后端容器、一次性 init/ingestion 任务
- 形成“必须自动恢复”和“必须禁止自动恢复”清单

## 影响范围
- `docker-compose.yml`
- `docker-compose-app.yml`
- `docker-compose.legacy.yml`
- `worklog/v2.2.2/sprint-10-202603/features/F1-容器自恢复策略/`

## 验证
- [ ] 人工对比三份 compose 的 restart 配置
- [ ] 输出服务分组清单并写入任务文档

## 完成标准
- [ ] 缺失 restart 的长期运行容器被完整识别
- [ ] 一次性任务与长期运行容器边界明确
