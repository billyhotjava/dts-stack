# Sprint-6：CLI 工具抽象 + 批量导入

## 背景

Sprint-5 的部署脚本（deploy.sh）将 Plan 创建和模型导入硬编码在单个领域目录中，不可复用。前端导入弹窗只支持单个模型，无法批量操作。

## 目标

1. 将部署脚本抽象为通用 CLI 工具集（dts-plan / dts-deploy / dts-manifest-gen）
2. 前端新增批量导入功能（ZIP 上传 + 多文件在线编辑）
3. 后端新增 batch-import API
4. Excel 入湖关联 ODS 表自动映射字段

## 设计文档

- `design.md` — CLI 工具 + 批量导入设计
- `ods-field-mapping-design.md` — Excel 入湖关联 ODS 表设计

## 任务清单

| 编号 | 任务 | 优先级 | 状态 | 产物位置 |
|------|------|--------|------|----------|
| S6-001 | lib/dts-common.sh 共享函数库 | P0 | DONE | `bin/lib/dts-common.sh` |
| S6-002 | dts-plan CLI | P0 | DONE | `bin/dts-plan` |
| S6-003 | dts-deploy 编排器 | P0 | DONE | `bin/dts-deploy` |
| S6-004 | dts-manifest-gen TSV 生成器 | P1 | DONE | `bin/dts-manifest-gen` |
| S6-005 | dts-dbt-import 改造（source common） | P1 | DONE | `bin/dts-dbt-import` |
| S6-006 | 业务文件迁移（bin/ → dbt/deploy/） | P0 | DONE | `services/dts-dbt/deploy/` |
| S6-007 | 后端 batch-import API | P0 | DONE | `ModelingSqlModelResource.java` |
| S6-008 | 前端批量导入 UI | P0 | DONE | `SqlModelingPage.tsx` |
| S6-009 | worklog 路径引用更新 | P2 | DONE | `worklog/v2.2.1/sprint-5/` |
| S6-010 | dts-pack 离线打包 CLI | P0 | DONE | `bin/dts-pack` |
| S6-011 | dts-deploy --package 模式 | P1 | DONE | `bin/dts-deploy` |
| S6-012 | Excel 入湖关联 ODS 表自动匹配字段 | P0 | DONE | `TransformCreatePage.tsx` |
| S6-013 | SqlFieldNameResolver 字典扩展 | P1 | DONE | `SqlFieldNameResolver.java` |
