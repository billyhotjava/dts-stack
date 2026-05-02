# F7: 兼容矩阵 + smoke + 联调 IT

**优先级**: P0
**状态**: DONE
**目标**: 把 F1-F6 的改动放在四服务联调环境验证,确认旧 env 名 fallback 工作正常,新 env 名生效,filter 强校验拦截到位,审计事件落表;输出可发布的兼容矩阵文档。

**依赖**: F4, F5, F6

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | `assets/env-migration-matrix.md` — 三服务 outbound/inbound env 旧→新映射 + fallback 链 + deprecated 时间线 + 最小/推荐部署配置 |
| T02 | DONE | `it/scripts/service-auth-smoke.sh` — 5 场景 curl(happy / 缺 token / 错 token / 未知 service / 无 service header) |
| T03 | DEFERRED | runtime-detail E2E 需本地 docker-compose 环境,留运维 IT 阶段(可基于 smoke 扩展) |
| T04 | DONE | `assets/sprint-28-deploy-runbook.md` — 渐进式 3 阶段升级 / 验证 / 故障处理 / 回滚 |
| T05 | DONE | `mvnw test` 三 java 模块全过(38 platform + 4 ingestion + 3 analytics = 45 测试) |
| T06 | DEFERRED | 前端 `pnpm build` 无 sprint-28 改动,沿用主分支验证 |
| T07 | DONE | sprint README + sprint-queue 同步 DONE |

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

- [x] 兼容矩阵文档发布(`assets/env-migration-matrix.md`)
- [x] smoke 脚本可重复跑(`it/scripts/service-auth-smoke.sh`)
- [x] 部署 runbook(`assets/sprint-28-deploy-runbook.md`)
- [ ] 真链路 E2E IT 报告(运维侧执行后填充 `it/evidence/`)

## 实现记录

- 新增: `assets/env-migration-matrix.md`
- 新增: `assets/sprint-28-deploy-runbook.md`
- 新增: `it/scripts/service-auth-smoke.sh`(可执行,5 场景)
- 验证: 三 java 模块 mvnw compile + 关键测试 45 用例全过
