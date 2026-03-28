# Sprint-19: 统一资产权限管控

**时间**: 2026-03
**状态**: READY
**目标**: 实现全资产按部门隔离的统一权限管控，以 platform 为权限中心，analytics 作为消费方

## 背景

数据从源系统进入 ODS 后，默认为机密级别，需按部门隔离管理。当前三个模块（admin/platform/analytics）各自维护独立权限体系，analytics 甚至 `.anyRequest().permitAll()`。需要建立以 platform 为中心的统一资产权限服务，覆盖表、卡片、仪表盘、大屏、模型全部资产类型。

## 设计文档

- [统一资产权限设计](../../../docs/superpowers/specs/2026-03-28-unified-asset-permission-design.md)

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 资产权限数据模型与服务 | 5 | DONE |
| F2 | Analytics 权限统一 | 4 | READY |
| F3 | 前端权限管理界面 | 5 | READY |
| F4 | Analytics 前端适配 | 3 | READY |
| F5 | 数据迁移与集成测试 | 3 | READY |

## 完成标准

- [ ] platform 侧资产归属与授权 API 完整可用
- [ ] analytics 所有资产请求经 platform 权限判定
- [ ] analytics 遗留权限代码（Permissions Graph、Group、Screen ACL）清理完毕
- [ ] 前端资产归属管理、授权管理、我的授权、审计日志四个页面上线
- [ ] 现有数据迁移完成，无权限数据丢失
- [ ] 集成测试通过：角色矩阵全覆盖验证
