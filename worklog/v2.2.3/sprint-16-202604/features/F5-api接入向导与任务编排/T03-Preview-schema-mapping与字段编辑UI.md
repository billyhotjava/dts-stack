# T03: Preview、schema snapshot 与 stg 字段编辑 UI

**优先级**: P1
**状态**: DRAFT
**依赖**: F2/T03, F3/T02, F3/T03

## 目标

提供从 API preview 到 schema snapshot，再到 stg 字段映射的可视化配置流程。ODS 阶段只确认 record path、目标 ODS 表名和原始 record 落地契约。

## 范围

- 配置 endpoint path、method、query/body 测试值和 record path。
- 展示 preview sample、字段推断结果、类型置信度和异常提示。
- 支持编辑 stg 列名、类型、nullable、敏感标记、主键。
- 保存 schema snapshot version 和 stg mapping version。
- 不提供 ODS 字段改名、类型覆盖、额外业务列配置。

## 完成标准

- [ ] 用户能定位 record path 无命中问题。
- [ ] 类型冲突和敏感字段有明显提示。
- [ ] ODS 表名有冲突校验；stg 字段名有冲突校验。
- [ ] 保存后 payload 符合后端契约。

## 2026-04-27 Sprint-18 回灌修正

- 已从现有数据库/文件统一向导的 API 分支移除 `apiFieldsJson` / `resource.fields` 提交入口。
- API 分支前端只保存 resource path、record path、query/body、pagination、cursor；字段推断结果后续进入 schema snapshot 和 stg mapping。
- 后端 contract `1.1.0` 增加 `odsLanding`，前端展示 API ODS 原始记录落地提示，避免用户误以为这里在编辑 ODS 字段。
