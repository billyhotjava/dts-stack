# T02: 统一关键容器 restart 策略并明确排除一次性任务

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
为关键运行服务补齐统一的 restart 策略，同时避免 init/一次性任务进入重启循环。

## 技术设计
- 对关键前端、后端、代理、数据库、编排服务设置 `restart: unless-stopped`
- 保持 `dts-airflow-init`、`dts-openmetadata-init`、一次性 ingestion 等任务为 `restart: "no"`
- 保证 normal/legacy 两套 compose 行为一致

## 影响范围
- `docker-compose.yml`
- `docker-compose-app.yml`
- `docker-compose.legacy.yml`
- 相关回归测试

## 验证
- [ ] Compose 配置变更后通过静态校验
- [ ] 自动化测试覆盖关键服务 restart 策略

## 完成标准
- [ ] 关键服务 restart 策略统一
- [ ] 一次性任务不会因 restart 策略误变为循环任务
