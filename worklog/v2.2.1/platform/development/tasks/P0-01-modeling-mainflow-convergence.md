# P0-01 逻辑建模主流程收敛

- 优先级：P0
- 状态：done

## 范围

- 收敛“逻辑建模”到单主流程：ODS 一键生成 + 单模型文件导入。
- 下线 ZIP 项目包导入入口与服务端接口。

## 子任务

- 前端移除 `SqlModelingPage.tsx` 中 `import-project` 相关按钮、弹窗、文案。
- 前端 API 层删除 `importSqlProjectZip` 调用。
- 后端下线 `ModelingSqlModelResource.java` 的 `/import-project` 接口。
- 下线 `ModelingSqlProjectImportService.java`（若无其他依赖）。
- 更新“简要说明”文案，避免新旧流程混用。

## 预计影响文件

- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- `source/dts-platform-webapp/src/api/platformApi.ts`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelingSqlModelResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlProjectImportService.java`（评估后删除或保留）

## 实施步骤

1. 先删前端入口（菜单、弹窗、文案），确保用户路径单一。
2. 再删后端接口并清理依赖，避免前后端版本错配。
3. 最后补回归，确认 ODS 一键生成、单文件导入、dbt 文件浏览不受影响。

## 回归命令（执行时记录结果）

- `pnpm -C source/dts-platform-webapp build`
- `mvn -f source/dts-platform/pom.xml -DskipTests compile`

## 已完成进展（2026-02-16）

- 已移除前端 ZIP 项目包导入能力：
  - 删除 API 调用：`source/dts-platform-webapp/src/api/platformApi.ts`
  - 删除建模页入口/弹窗/提交逻辑：`source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- 已下线后端 ZIP 项目包导入接口：
  - 删除 `/api/modeling/sql-models/import-project`：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelingSqlModelResource.java`
- 已清理 ZIP 导入服务残留代码：
  - 删除 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlProjectImportService.java`
  - 删除 `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlProjectImportServiceTest.java`

## 回归结果（2026-02-16）

- `pnpm -C source/dts-platform-webapp build`：通过
- `mvn -f source/dts-platform/pom.xml -DskipTests compile`：通过

## 验收标准

- 页面不可见 ZIP 导入入口。
- API 无 `/api/modeling/sql-models/import-project` 路径。
- ODS 一键生成流程不受影响。

## 风险与回滚

- 风险：现场仍依赖 ZIP 导入。
- 回滚：保留特性开关 `enableProjectZipImport=false`，必要时临时开启。
