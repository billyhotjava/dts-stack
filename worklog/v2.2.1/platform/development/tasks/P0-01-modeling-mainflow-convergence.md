# P0-01 逻辑建模主流程收敛

- 优先级：P0
- 状态：planned

## 范围

- 收敛“逻辑建模”到单主流程：ODS 一键生成 + 单模型文件导入。
- 下线 ZIP 项目包导入入口与服务端接口。

## 子任务

- 前端移除 `SqlModelingPage.tsx` 中 `import-project` 相关按钮、弹窗、文案。
- 前端 API 层删除 `importSqlProjectZip` 调用。
- 后端下线 `ModelingSqlModelResource.java` 的 `/import-project` 接口。
- 下线 `ModelingSqlProjectImportService.java`（若无其他依赖）。
- 更新“简要说明”文案，避免新旧流程混用。

## 验收标准

- 页面不可见 ZIP 导入入口。
- API 无 `/api/modeling/sql-models/import-project` 路径。
- ODS 一键生成流程不受影响。

## 风险与回滚

- 风险：现场仍依赖 ZIP 导入。
- 回滚：保留特性开关 `enableProjectZipImport=false`，必要时临时开启。
