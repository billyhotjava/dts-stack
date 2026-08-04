# 发布安全计划（Gate G3）

**变更类型**：文件接入生命周期语义调整  
**风险等级**：中（准入方法调用面较大，但变更仅移除文件质量检测门禁）

## 1. 迁移策略

本次无数据库结构变更、数据回填、Liquibase 变更或 API 字段变更。已有任务、质量结果和暂存数据均不自动修改。

## 2. 兼容性

| 消费方 | 是否受影响 | 处置 |
| --- | --- | --- |
| 文件接入新建/编辑向导 | 是 | 保存后统一准入并立即生效 |
| 文件计划详情页 | 是 | 质量检测保留为可选功能，不再自动准入 |
| 数据库/API 接入 | 否 | 保持现有保存、准入与执行流程 |
| 数据质量模块 | 否 | 规则、运行和结果结构均不变 |

## 3. 回滚

- 后端基线：`dts-ingestion:rollback-before-file-quality-optional-20260804`，镜像 `sha256:1e96b2889ad7...`
- 前端基线：`dts-platform-webapp:rollback-before-file-quality-optional-20260804`，镜像 `sha256:d3c852d7ce4b...`
- 回滚顺序：先后端，确认健康后再前端。

```bash
docker tag dts-ingestion:rollback-before-file-quality-optional-20260804 dts-ingestion:1.0.0
docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-ingestion
docker tag dts-platform-webapp:rollback-before-file-quality-optional-20260804 dts-platform-webapp:1.0.0
docker compose -f docker-compose-app.yml up -d --no-deps --force-recreate dts-platform-webapp
```

镜像标签与镜像 ID 已验证可解析。未在当前生产服务上实际切回旧镜像，以免制造二次中断；因此完整回滚演练记为 GAP，回滚产物可用。

## 4. 部署结果

1. `dts-ingestion:1.0.0` 已更新为 `sha256:bd4515d00902...`，容器健康。
2. `dts-platform-webapp:1.0.0` 已更新为 `sha256:f3ceb160702c...`，容器运行且首页静态响应正常。
3. 已有草稿不会被后台自动生效；用户再次编辑并保存后走新的立即生效流程。
