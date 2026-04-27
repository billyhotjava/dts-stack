# T04: Secret 写入、脱敏回显与轮换入口

**优先级**: P0
**状态**: DRAFT（等待 F1/T05 secret 表落地）
**依赖**: F1/T02, F1/T05

## 目标

建立 API 数据源密钥生命周期入口，覆盖创建、更新、脱敏展示、版本标记和轮换。

## 范围

- 保存 secret 时生成 `secretRef` 和 `secretVersion`。
- 查询详情只返回 `maskedDisplay`、更新时间和版本。
- 更新 secret 后触发数据源配置版本变化。
- 预留后续密钥轮换和过期提醒字段。

## 数据模型对齐（与 F1/T05 同源）

落库表：`infra_data_source_secret`（拟用 changeset `20260428_01_api_data_source_secret.xml`）。字段定义见 F1/T05 表 schema 草案。

写入路径：
- API：`POST /api/datasources/{id}/secrets`、`PATCH /api/datasources/{id}/secrets/{providerId}/{fieldName}`
- 入参：明文 secret value（仅 in-flight，落库前转换为 secretRef）
- 出参：`{providerId, fieldName, maskedDisplay, secretVersion, updatedAt, status}`，**永远不回显明文**

读取路径：
- 详情接口 `GET /api/datasources/{id}` 返回 `secretSummaries: [{providerId, fieldName, maskedDisplay, secretVersion, rotatedAt, status}]`
- 任务运行时通过 `secretRef`（不直接读 `field_name`）获取明文

`maskedDisplay` 生成规则（默认）：保留前 2 字符 + `***` + 末 4 字符，不足 8 字符则全部 `***`。

## 完成标准

- [ ] 前端永远拿不到 secret 明文 —— 验收口径：详情接口契约测试 + ESLint 规则 ban `secretValue`/`apiKey` 字段名出现在响应类型。
- [ ] 任务执行只通过 secretRef 获取运行时密钥 —— 验收口径：`grep -rn "secretValue\|plaintextSecret" source/dts-ingestion/src/main/java` 仅出现于 secret backend 内部调用栈。
- [ ] secret 更新不会破坏历史任务配置读取 —— `secret_version` 持久化，老 execution 引用旧 version；新建/重跑使用 ACTIVE version。
- [ ] 审计记录只保存动作和 masked id —— 审计 payload schema 中 secret 字段类型限制为 `secretRef + maskedDisplay`，不含 `value`/`plaintext`。
- [ ] 轮换入口（`POST /secrets/{...}/rotate`）写入新 version、旧 version 标 `ROTATED`，旧 version 保留至少 N 天供回滚。

## 实现进展 / 关联代码

- 待办（依赖 F1/T05 表落地）：`InfraDataSourceSecretEntity`、`InfraDataSourceSecretRepository`、`SecretService`、`InfraDataSourceResource` 详情接口扩展、masked display utility。

