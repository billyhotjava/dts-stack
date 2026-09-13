# IT-01 commands

执行时间：2026-07-31 02:24–02:58 CST
环境：`https://bi.yuzhicloud.com`；Google Chrome `150.0.7871.128`

## 构建与静态验证

```bash
cd source/dts-platform && npm run java:jar:prod
cd source/dts-admin && npm run java:jar:prod
cd source/dts-platform-webapp && pnpm build

docker build --build-arg ENABLE_MAVEN_BUILD=false \
  -t dts-platform:2.2.3-sprint79-239fba2c7 \
  -f builds/dts-platform/Dockerfile .
docker build --build-arg ENABLE_MAVEN_BUILD=false \
  -t dts-admin:2.2.3-sprint79-8742e2bfe \
  -f builds/dts-admin/Dockerfile .
docker build --build-arg PNPM_VERSION=10.28.0 \
  -t dts-platform-webapp:2.2.3-sprint79-239fba2c7 \
  -f builds/dts-platform-webapp/Dockerfile .
```

结果：platform/admin `BUILD SUCCESS`；webapp TypeScript/Vite exit 0，转换 10,678 个模块；三个 Docker 构建均 exit 0。

## 部署与回滚演练

```bash
docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate --wait dts-platform
docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-platform-webapp
docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate --wait dts-admin
docker compose -f docker-compose-app.yml restart dts-airflow-scheduler dts-airflow-webserver
```

精确断言：

```text
rollback platform = sha256:46b0435fc37290e71fe0c995d31b7c724e0cef84c85a12422b549eb4e11d2902
rollback webapp  = sha256:6ad5170b1c5ca5665061f00121881f908a58379d52b2db51942c4a8969854b19
rollback admin   = sha256:941e5e461e25c8b6f400b750f0ccf04a1b12611f42142d51508626da7835f522
release platform = sha256:70dbc04c8f8468b084ded7721aed9db59e836967ab53333e0990c2eadf65b188
release webapp   = sha256:d067edfaac774c9936e694e27a2876a7f5c40089d5ed89faded7e1e4b86004f2
release admin    = sha256:524a6e79d0e725888ab1fc772a617c93334c6c08c88793565b94a8a636183263
old/restored HTTPS = 200/200
menu rollback/restore = UPDATE 5/UPDATE 5; visibility bindings = 5
```

## 登录态浏览器验收

```bash
SPRINT79_CONFIRM_PRODUCTION_READ_ONLY=1 \
worklog/v2.2.3/sprint-79-202607-modeling-workspace-convergence/assets/run-authenticated-e2e.sh \
  e2e/sprint79-modeling-workspace.spec.ts
```

脚本创建一次性 Keycloak 用户，只授予 `ROLE_OP_ADMIN`，写入同 ID 的管理员快照；随机口令只存在于进程内。执行结束后精确清理。

```text
1 passed
CLEANUP keycloak=0 snapshot=0 auth_state=absent
```

前三次执行依次暴露“默认计划已选中”“指标直接嵌入 owner”“新关系图替换旧血缘页”三处测试契约漂移；对应测试提交为 `e1658f9d7`、`3e370f46e`、`9953198de`。失败截图均显示产品页面已正常加载，最终同一精确旅程通过。

未记录任何真实口令、token 或 Cookie。
