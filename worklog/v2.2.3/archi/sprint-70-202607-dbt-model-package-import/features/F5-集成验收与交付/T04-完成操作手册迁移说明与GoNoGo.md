# T04：完成操作手册、迁移说明与 Go/No-Go

**优先级**: P0  
**状态**: READY  
**依赖**: T03

## 目标

将导入流程、兼容边界、故障处理和发布判断交付给实际用户和运维人员。

## 技术设计

- 更新 PJM UI 手工录入指南，增加“生成包/导入包”快捷路径。
- 说明旧 SQL/ZIP 导入、vNext dbt import 和新 canonical package import 的边界。
- 提供常见 BLOCKED/CONFLICT/STALE 修复手册。
- 汇总代码、测试、数据库、Chrome95 和遗留风险，形成 Go/No-Go。

## 影响范围

- Sprint-70 IT/evidence。
- `worklog/v2.2.3/s10/v4/models` 操作手册。
- 发布说明。

## 验证

- [ ] 新用户可从建模工作台完成导入，无需复制技术 ID。
- [ ] 旧入口仍可定位且不会被误认为 canonical 主线。
- [ ] Go/No-Go 每项都有证据链接。

## 完成标准

- [ ] 文档与最终页面、API 和错误文案一致。
- [ ] 未关闭风险明确记录 owner 和后续 Sprint，不伪装完成。
