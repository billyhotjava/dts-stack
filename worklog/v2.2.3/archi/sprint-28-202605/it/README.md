# Sprint-28 IT(集成验收)

## 验收范围

四服务联调:`dts-platform` + `dts-ingestion` + `dts-analytics` + `dts-platform-webapp`

## 场景清单

| 场景 ID | 描述 | 状态 |
|---------|------|------|
| S1 | 仅设新 env(`DTS_INGESTION_TO_PLATFORM` 等),入湖任务 happy path 200 | TODO |
| S2 | 仅设旧 env(`DTS_ADMIN_SERVICE_TOKEN`),四服务全 fallback 工作 | TODO |
| S3 | filter 强校验:不带 token → 403,带错 token → 403,带正确 token → 200 | TODO |
| S4 | 越权面修复:任意非 runtime-detail 但要求 OP_ADMIN 的 platform 端点,仅靠 header → 403 | TODO |
| S5 | legacy-header-only-mode=true 时回退到 Sprint-27 行为 | TODO |
| S6 | filter 拒绝路径结构化日志 + SERVICE_AUTH_DENIED 审计事件落表 | TODO |
| S7 | analytics 三个 client 的出站调用全部带正确 token | TODO |
| S8 | analytics → admin 审计上报 header 正确 | TODO |
| S9 | mvn 全模块测试 pass | TODO |
| S10 | pnpm build pass | TODO |

## Evidence 目录约定

```
it/evidence/<YYYYMMDD>-<env>/
  smoke-S1-happy.log
  smoke-S3-strict-auth.log
  curl-runtime-detail-200.txt
  curl-runtime-detail-403-no-token.txt
  audit-service-auth-denied.sql
  maven-test.log
  pnpm-build.log
  README.md(场景对应文件索引)
```

## 工具脚本

- `scripts/service-auth-smoke.sh` — 服务鉴权 4 类场景
- `scripts/runtime-detail-e2e.sh` — 入湖 E2E 闭环

## 收口

所有场景 PASS 后:
1. 更新 sprint README 状态为 DONE
2. 更新 sprint-queue.md
3. 打 git tag `sprint-28-done`(可选)
4. 提示运维下个 sprint 删除 deprecated env
