# T03: 审计中心 analytics 展示映射

**优先级**: P0
**状态**: DONE
**依赖**: T02

## 目标

审计中心展示 `sourceSystem=analytics` 时，不再把 BI 操作显示成系统管理/管理端审计。

## 技术设计

- `AuditEntryViewMapper.mapSourceSystemText` 将 analytics 映射为 `BI分析`。
- `AuditEntryViewMapper.mapLogType` 将 analytics 映射为 `分析端审计`。

## 影响范围

- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/audit/AuditEntryViewMapper.java`

## 验证

- [x] `AuditEntryViewMapperTest`

## 完成标准

- [x] analytics 审计记录不会再归为管理端审计。
