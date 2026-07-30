# IT-01 commands

执行时间：2026-07-30 20:01–20:17 CST
环境：`https://bi.yuzhicloud.com`；Google Chrome `150.0.7871.128`

## 构建与静态验证

```bash
node --test src/pages/modeling/ModelingWorkspacePanels.source-contract.test.ts
pnpm exec tsc --noEmit
docker build -t dts-platform-webapp:1.0.0 \
  -f builds/dts-platform-webapp/Dockerfile .
```

结果：source-contract 5/5、TypeScript、生产构建均为 exit 0；Vite 转换 10,665 个模块。

## 部署与回滚演练

```bash
docker tag <actual-running-image> dts-platform-webapp:rollback-sprint79-f1t02
docker tag dts-platform-webapp:1.0.0 dts-platform-webapp:sprint79-f1t02
docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-platform-webapp

docker tag dts-platform-webapp:rollback-sprint79-f1t02 dts-platform-webapp:1.0.0
docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-platform-webapp

docker tag dts-platform-webapp:sprint79-f1t02 dts-platform-webapp:1.0.0
docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-platform-webapp
```

精确断言：

```text
rollback = sha256:811ab4777cec9eac355812c6ccecc97d40350521f5f63457eba303aa67ca171c
release  = sha256:7b939a81115eeda33c7e37800cf65e9ae3a507eaae0b9f228e9cf30a77130403
old HTTPS = 200
restored HTTPS = 200
```

## 登录态浏览器验收

```bash
worklog/v2.2.3/sprint-79-202607-modeling-workspace-convergence/assets/run-authenticated-e2e.sh \
  e2e/sprint79-modeling-workspace.spec.ts
```

脚本创建一次性 Keycloak 用户，只授予 `ROLE_MODEL_MAINTAINER`，写入同 ID 的管理员快照；随机口令只存在于进程内。执行结束后精确清理。

```text
1 passed
CLEANUP keycloak=0 snapshot=0 auth_state=absent
```

未记录任何真实口令、token 或 Cookie。
