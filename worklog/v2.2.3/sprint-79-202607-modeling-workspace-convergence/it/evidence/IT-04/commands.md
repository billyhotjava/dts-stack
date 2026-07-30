# IT-04 commands

执行时间：2026-07-31 02:58 CST  
环境：部署后的 DTS 主线候选；Google Chrome `150.0.7871.128`

```bash
SPRINT79_CONFIRM_PRODUCTION_READ_ONLY=1 \
worklog/v2.2.3/sprint-79-202607-modeling-workspace-convergence/assets/run-authenticated-e2e.sh \
  e2e/sprint79-modeling-workspace.spec.ts
```

精确结果：

```text
1 passed
关系图：4 个节点，4 条关系
page errors = 0
request failures = 0
API 4xx/5xx = 0
CLEANUP keycloak=0 snapshot=0 auth_state=absent
```

运行镜像：

```text
dts-platform        sha256:70dbc04c8f8468b084ded7721aed9db59e836967ab53333e0990c2eadf65b188
dts-platform-webapp sha256:d067edfaac774c9936e694e27a2876a7f5c40089d5ed89faded7e1e4b86004f2
```

截图复用 `../IT-01/workspace-seven-modules.png`；其中展示部署后的建设计划关系图。
