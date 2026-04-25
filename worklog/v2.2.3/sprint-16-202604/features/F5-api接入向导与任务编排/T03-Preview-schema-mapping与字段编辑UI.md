# T03: Preview、schema mapping 与字段编辑 UI

**优先级**: P1
**状态**: DRAFT
**依赖**: F2/T03, F3/T02, F3/T03

## 目标

提供从 API preview 到 ODS 字段映射的可视化配置流程。

## 范围

- 配置 endpoint path、method、query/body 测试值和 record path。
- 展示 preview sample、字段推断结果、类型置信度和异常提示。
- 支持编辑 ODS 列名、类型、nullable、敏感标记、主键。
- 保存 mapping version。

## 完成标准

- [ ] 用户能定位 record path 无命中问题。
- [ ] 类型冲突和敏感字段有明显提示。
- [ ] ODS 表名和字段名有冲突校验。
- [ ] 保存后 payload 符合后端契约。

