# P2-02 模型质量基线（自动测试模板）

- 优先级：P2
- 状态：done

## 范围

- 对 ODS 一键生成的 DWD/DWS/ADS 模型自动附带质量测试模板。

## 子任务

- 生成 `schema.yml` 基础测试：`not_null`、`unique`、`relationships`（按可识别主外键）。
- 与字段标准/元数据标准联动，补齐字段描述与类型校验。
- 在发布前加入质量门禁提示。

## 验收标准

- 新生成模型默认带测试定义。
- 测试失败时发布动作有明确阻断或告警。

## 风险与回滚

- 风险：自动推断主键不准确。
- 回滚：初版仅生成不阻断，允许人工确认后生效。

## 已完成进展（2026-02-17）

- ODS 一键生成链路已自动生成质量模板（`*.yml`）：
  - 生成位置：模型 SQL 同目录（如 `models/dwd/<project>/dwd_xxx.yml`）
  - 包含测试模板：`not_null`、`unique`、`relationships`（按主外键命名启发式）
  - 包含字段元信息：`expected_data_type`、`standard_id`、`standard_rule`
  - 仅覆盖平台自动生成的模板（含标记头）；手工维护 yml 默认不覆盖
- 质量模板生成结果已回传到“一键生成”结果弹窗：
  - 新增：`qualityTemplatesGenerated`
  - 新增：`qualitySkipped`
- 发布前质量门禁提示已接入：
  - 新增后端接口：`POST /api/etl/dbt/quality-gate/check`
  - 规则：
    - 最近一次测试类构建失败（test/build）=> 阻断上线
    - 模型缺少测试模板或缺少类型元信息 => 告警确认后可继续
  - “提交上线”动作在触发 dbt run 前会先执行门禁检查
- 历史 selector 兼容修复：
  - 建模页面默认 selector 从 `tab:` 统一迁移到 `tag:`（前端兼容转换）
  - 后端模型 DTO / 新写入 `dagSelector` 统一为 `tag:xxx`

## 影响文件

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtQualityGateService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- `source/dts-platform-webapp/src/api/platformApi.ts`
- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`

## 回归结果（2026-02-17）

- `mvn -f source/dts-platform/pom.xml -DskipTests compile`：通过
- `pnpm -C source/dts-platform-webapp build`：通过
