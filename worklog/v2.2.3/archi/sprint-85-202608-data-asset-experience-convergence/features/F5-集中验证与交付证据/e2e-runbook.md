# Sprint-85 E2E Runbook（一次性联合验证）

**状态**：BLOCKED_E2E_INPUT（需授权账号 + Chrome 实机，按 G4 约定不标记 REAL/DELIVERED）
**前置**：最终镜像 `dts-platform-webapp:1.0.0`（digest `sha256:2442012bd2526389…`）已部署，全部路由 200。
**执行账号**：待提供（需具备：资产查看、代申请权限、审批、我的授权查看、审计查看）。

## 目标链路（一次走通，逐项截图留证）

```text
数据治理 → 数据地图与资产
  1. 资产概览(/catalog/assets)
     - 标题为「资产概览」（非资产地图）；矩阵 drill「其他域(N)」→ 台账带真实范围
     - 治理缺口「未定密/失效」下钻 → 台账 URL 带 ?unclassified=1 / ?stale=1 且筛选生效（提示条）
     - 快捷入口「查看血缘图谱」「申请权限」可达
  2. 资产台账(/catalog/assets/ledger)
     - 行级「治理资产」「申请权限」两个操作
     - 「申请权限」→ /security/dataset-access-approval?action=new&assetId={id}&assetType=dataset
  3. 数据搜索(/catalog/search)
     - 深链 ?unclassified=1 进入后 v2 检索带治理缺口筛选并显示提示条
  4. 详情(/catalog/datasets/{id})
     - 单一事实源渲染；「血缘与影响」Tab 只展示 DTS 影响链路 + OM 同步证据说明（不渲染第二张图）
     - 「查看完整血缘分析」深链到图谱 ?datasetId=
  5. 血缘图谱(/catalog/lineage/graph?direction=BOTH&depth=3&...)
     - 筛选全部在 URL；刷新恢复；关键词输入即过滤（图/表/导出同一份数据）
  6. 权限申请（对话框）
     - 资产被预填（名称/分层/密级/归属）；提交成功 → 自动切到「我的申请」并提示
  7. 审批（/security/dataset-access-approval）
     - 标题「数据资产 · 权限申请与审批」；「我的授权」「授权管理」「查看审计」侧边入口
  8. 旧深链 /catalog/asset-detail?id={uuid} → 302 到 /catalog/datasets/{id}；无 id → 台账
```

## 证据登记（执行后回填）

| # | 步骤 | 命令/截图 | 深链参数断言 | 通过 |
|---|---|---|---|---|
| 1 | 概览 drill 未定密 | screenshot | URL 含 unclassified=1，台账提示条出现 | ☐ |
| 2 | 台账行级申请权限 | screenshot + API | action=new&assetId 与行 id 一致 | ☐ |
| 3 | 搜索治理缺口深链 | screenshot | 搜索页显示缺口提示条，v2 结果被过滤 | ☐ |
| 4 | 详情血缘 Tab | screenshot | 仅一张图 + OM 证据说明 | ☐ |
| 5 | 图谱 URL 刷新 | screenshot | 筛选恢复，datasetId 定位同一数据集 | ☐ |
| 6 | 申请提交 | screenshot + 审计 | 我的申请出现新记录（高亮/置顶） | ☐ |
| 7 | 审批→我的授权 | screenshot | 批准后 /my/asset-grants 可见授权 | ☐ |
| 8 | asset-detail 旧链 | curl + screenshot | 跳转详情或台账，无 404 死角 | ☐ |

## 回滚锚点

- 当前：`sha256:2442012bd2526389eadbec1381eb5ee338bce825345777fef45716102478fb5f`（dts-platform-webapp:1.0.0）
- 回滚：`sha256:e1c63c07d97c1bc62f2c66d9b5…`（上一镜像）
- 命令：`docker compose -f docker-compose-app.yml up -d --no-deps dts-platform-webapp`（tag 不变，改镜像引用即可）
