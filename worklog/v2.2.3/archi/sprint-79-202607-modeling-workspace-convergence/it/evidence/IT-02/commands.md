# IT-02 commands

执行时间：2026-07-30 21:11–22:14 CST
环境：`https://bi.yuzhicloud.com`；Google Chrome `150.0.7871.128`

## 聚焦测试与构建

```bash
node --test \
  src/pages/modeling/canonicalModelingPages.source-contract.test.ts \
  src/pages/modeling/modelSpecCompactFields.source-contract.test.ts \
  src/pages/modeling/modelSpecIssueFieldPath.test.ts \
  src/pages/modeling/modelSpecWorkbench.test.ts
pnpm exec tsc --noEmit
pnpm exec biome check \
  src/pages/modeling/ModelSpecDetailPage.tsx \
  src/pages/modeling/canonicalModelingPages.source-contract.test.ts
docker build -t dts-platform-webapp:sprint79-f2t01-aacf2d660 \
  -f builds/dts-platform-webapp/Dockerfile .
```

结果：聚焦测试 31/31、TypeScript、Biome 和生产镜像构建均为 exit 0；Vite 转换 10,671 个模块。

## 真实认证只读验收（更正）

```bash
worklog/v2.2.3/sprint-79-202607-modeling-workspace-convergence/assets/run-authenticated-e2e.sh \
  e2e/sprint79-model-detail.spec.ts \
  --grep "renders one logical canvas"
```

精确结果：

```text
ModelSpec listCount=6
1 passed
Chrome 150.0.7871.128
modelingWrites=0
CLEANUP keycloak=0 snapshot=0 auth_state=absent
```

初次记录的 `3 passed` 已被独立审查判定为假通过：旧测试只拦截 5xx、写请求仅事后审计，
且缺少代表数据时会 skip。更正后的测试在首个页面请求前阻断所有 `/api/modeling/**`
非安全方法，并把全部 4xx/5xx 记为失败。

首次更正运行暴露了 plan policy 404；根因是一次性账号使用了不属于 DTS 组织权限合同的
`ROLE_MODEL_MAINTAINER`。当前 realm 又缺少代码期望的 `ROLE_INST_DATA_OWNER` 和
`ROLE_EMPLOYEE`，因此本轮改用 realm 中真实存在的临时 `ROLE_OP_ADMIN`，并在 Keycloak
与管理员快照两处保持一致。请求前写屏障仍生效，测试未触发 ModelSpec 保存、发布或删除。

## F2/T04 精确提交构建与工作台上下文验收

```bash
docker build \
  -t dts-platform-webapp:sprint79-f2t04-559fef0e5 \
  -f builds/dts-platform-webapp/Dockerfile .

SPRINT79_CONFIRM_PRODUCTION_READ_ONLY=1 \
  bash worklog/v2.2.3/sprint-79-202607-modeling-workspace-convergence/assets/run-authenticated-e2e.sh \
  source/dts-platform-webapp/e2e/sprint79-model-detail.spec.ts \
  --grep "renders one logical canvas|workbench restores"
```

精确结果：

```text
commit=559fef0e5
candidate_image=sha256:3303dfb0ef122614a08a6293530e6b0848111b46b4b426ec95e7aff5801caaee
Vite modules transformed=10673
F2_LOGICAL_READONLY listCount=6 fields=1 writes=0
F2_WORKBENCH_CONTEXT listCount=6 writes=0
2 passed
Chrome 150.0.7871.128
CLEANUP keycloak=0 snapshot=0 auth_state=absent
```

第二个旅程从 workbench 的 model asset URL 恢复 canonical `ModelSpecDetailPage`，刷新后保持
对象上下文；在描述字段输入未保存标记，打开并关闭“发布结果”抽屉后标记仍保留；返回后只清理
`assetKind/assetId/activeStage`，保留 `module=models&workspaceView=model-specs`。全部
`/api/modeling/**` 非安全方法在发出前被阻断，所有建模 4xx/5xx 均会使测试失败。

候选仅临时部署用于验收。结束后恢复部署前并行会话镜像
`sha256:6ad5170b1c5ca5665061f00121881f908a58379d52b2db51942c4a8969854b19`，
容器为 running，`https://bi.yuzhicloud.com/` 返回 200。
