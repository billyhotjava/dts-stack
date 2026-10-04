# ELT 模型状态机设计

## 目标
为 SQL 模型增加生命周期状态流转，串联 commit → test → run → sync 操作，消除工作流割裂感。

## 状态定义

| 状态 | 含义 | 颜色 |
|------|------|------|
| DRAFT | 草稿/已修改 | 灰色 |
| COMMITTED | 已提交版本控制 | 蓝色 |
| TESTED | 已通过测试 | 橙色 |
| PUBLISHED | 已发布上线 | 绿色 |

## 状态流转规则

1. 新建模型 → DRAFT
2. 编辑 SQL/元数据 → 自动回退 DRAFT（无论当前状态）
3. Git commit 成功 → COMMITTED
4. dbt test 通过 → TESTED
5. dbt run 成功 + sync 完成 → PUBLISHED

## 快赢串联

1. **重命名**: "提交上线" → "提交变更"
2. **commit 后引导**: 成功后弹窗"是否运行测试？"
3. **run 后自动 sync**: dbt run 成功后自动调用 sync models
4. **文件编辑回写**: DbtFileService 保存时同步更新 modeling_sql_model.sql_text

## 后端改动

### ModelingSqlModelService
- `applyModelFields()` 中：编辑保存时 `model.setStatus("DRAFT")`（已有，无需改）

### DbtGitService / DbtGitResource
- commit 成功后：查找所有 git 变更涉及的 model path，批量更新 status → COMMITTED

### EtlResource
- dbt test 成功回调：更新关联 models status → TESTED
- dbt run 成功回调：更新关联 models status → PUBLISHED + 自动 sync

### DbtFileService
- saveFile() 增加：如果文件是 models/ 下的 .sql，反查 ModelingSqlModel 并更新 sql_text + status=DRAFT

## 前端改动

### SqlModelingPage.tsx
- 模型列表增加状态标签列（Badge 组件）
- 模型详情顶部增加状态进度条
- "提交上线" → "提交变更"
- commit 成功后 Modal.confirm("是否运行测试？")

### DbtFileBrowserPage.tsx
- 文件保存成功后刷新模型列表（如果有变更）

## 数据迁移
- 现有模型 status 为空或 DRAFT → 保持 DRAFT
- 无需 Liquibase migration（字段已存在）
