# F2: 分层注册表与依赖红线

**优先级**: P0
**状态**: READY

## 目标
分层从硬编码卡片升级为受控注册表对象，层间依赖方向可被门禁校验。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 分层注册表数据模型 | P0 | READY | - |
| T02 | workbench与规划页消费注册表 | P0 | READY | T01 |
| T03 | 依赖红线校验与门禁接入 | P0 | READY | T01 |
| T04 | 模型管理页全分层呈现 | P0 | READY | T01 |

## 完成标准
- [ ] 注册表含 5 层×职责×allowedUpstream×命名前缀（素材=model-governance）。
- [ ] workbench 硬编码 WAREHOUSE_LAYER_PLAN 移除。
- [ ] 违规流向（如 ADS 直读 ODS）被门禁阻断并说明红线出处。
- [ ] 模型管理页全层呈现（客户五层+STG 折叠），前端与 modelingApi 层枚举一致。
