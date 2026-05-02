# F7: 兼容矩阵 + smoke + 联调 IT

**优先级**: P0
**状态**: READY
**目标**: 把 F1-F6 的改动放在四服务联调环境验证,确认旧 env 名 fallback 工作正常,新 env 名生效,filter 强校验拦截到位,审计事件落表;输出可发布的兼容矩阵文档。

**依赖**: F4, F5, F6

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | READY | 编写 `assets/env-migration-matrix.md`:旧→新 env 映射、各级 fallback 规则、deprecated 时间线 |
| T02 | READY | `it/scripts/service-auth-smoke.sh`:启动后 curl 四类场景(happy / 缺 token / 错 token / 未知 service),验证 status code 与日志 |
| T03 | READY | `it/scripts/runtime-detail-e2e.sh`:用 docker-compose 启动 platform+ingestion,前端模拟新建数据连接 + 入湖任务,确认 ingestion → platform runtime-detail 200,采集执行成功 |
| T04 | READY | 部署 runbook `assets/sprint-28-deploy-runbook.md`:升级前后 env 切换步骤、回滚路径、legacy-mode 紧急开关使用方法 |
| T05 | READY | 跑 mvn 全模块测试,evidence 落 `it/evidence/<date>/maven-test.log` |
| T06 | READY | 跑 frontend `pnpm build`,evidence 落 `it/evidence/<date>/pnpm-build.log` |
| T07 | READY | 整体 IT pass 后,sprint README 状态置 DONE,sprint-queue 同步 |

## 矩阵示例(给 T01 参考)

| 旧 env | 新 env(优先) | 兼容 fallback | deprecated 计划 |
|--------|---------------|---------------|-----------------|
| `DTS_ADMIN_SERVICE_TOKEN`(出站 to admin) | `DTS_PLATFORM_TO_ADMIN` | 旧 → fallback 一个版本 | 下个 sprint 删 |
| `DTS_ADMIN_SERVICE_TOKEN`(入站) | `DTS_INBOUND_FROM_INGESTION` 等 | 旧作为 Map 值统一 fallback | 下个 sprint 删 |
| `DTS_PLATFORM_SERVICE_TOKEN`(ingestion 出站) | `DTS_INGESTION_TO_PLATFORM` | 旧 → 中级 fallback | 下个 sprint 删 |

## 验证

- [ ] smoke 4 类场景全过
- [ ] runtime-detail E2E:前端入湖任务从 403 修复到 200
- [ ] 仅设旧 env(模拟未完成迁移)所有调用仍能工作
- [ ] 仅设新 env 所有调用工作 + 旧 env deprecated 警告日志可见
- [ ] mvn test 全过,pnpm build 全过
- [ ] evidence 目录有完整截图/日志

## 完成标准

- [ ] 兼容矩阵文档发布(可分发给运维)
- [ ] smoke 脚本可重复跑(被 sprint 后续验收复用)
- [ ] IT 报告(it/README.md)结构化记录场景与结果
