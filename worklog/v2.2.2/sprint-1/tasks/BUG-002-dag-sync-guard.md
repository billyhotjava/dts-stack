# BUG-002: DAG 未就绪时禁止运行

- **优先级**: P0
- **状态**: TODO

## 问题

DAG 上传/同步需要 30-60 秒，用户在此期间点运行报 502。

## 方案

1. **前端**: 逻辑建模页面点"上线"前先调用 `GET /api/etl/dbt/sync/status` 检查 DAG 是否就绪
2. DAG 未就绪时禁用按钮，显示 "DAG 正在同步中，请稍后再试"
3. 可选：轮询检测直到就绪后自动启用

## 涉及文件

- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx` — 上线按钮逻辑
- `source/dts-platform/src/main/java/.../EtlResource.java` — sync status API
- `source/dts-platform/src/main/java/.../DbtArtifactSyncState.java` — 状态模型

## 交付标准

- [ ] DAG 未注册完成时，上线按钮灰置+提示
- [ ] DAG 就绪后自动恢复可点击
