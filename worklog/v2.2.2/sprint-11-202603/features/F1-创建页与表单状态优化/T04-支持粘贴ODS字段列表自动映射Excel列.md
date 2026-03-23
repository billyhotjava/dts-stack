# T04: 支持粘贴 ODS 字段列表自动映射 Excel 列

**优先级**: P1
**状态**: DONE
**依赖**: T02

## 目标
为 Excel 入湖创建页增加“粘贴 ODS 字段列表后按顺序自动覆盖 Excel 列名”的能力，适配离线 Excel 先做 ODS 设计的现场流程。

## 技术设计

- 在 [FileBasicStep.tsx](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/steps/FileBasicStep.tsx) 新增“粘贴 ODS 字段”入口
- 解析 `,` / `，` / 换行 / Tab 分隔的字段列表
- 复用现有 ODS 匹配展示语义：
  - `_odsMatched`
  - `_odsExtra`
  - 未匹配 ODS 字段列表
- 不改后端协议，仍通过 `_fileColumns` 提交结果

## 当前进展

- 已新增 helper：
  - [fileOdsPasteMapping.helpers.ts](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/fileOdsPasteMapping.helpers.ts)
- 已接入文件步骤 UI：
  - [FileBasicStep.tsx](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/steps/FileBasicStep.tsx)
- 已支持：
  - 逗号 / 中文逗号 / 换行 / Tab 解析
  - 顺序覆盖 Excel 列名
  - Excel 多余列标记为未关联
  - ODS 多余字段显示为未匹配字段

## 影响范围

- [FileBasicStep.tsx](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/steps/FileBasicStep.tsx)
- [TransformCreatePage.tsx](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx)
- 新增粘贴映射 helper 与测试

## 验证

- [x] helper 解析与映射测试通过
- [ ] 前端构建或等价 sanity check 通过
  当前全量 build 仅被并发改动 [index.tsx](/opt/prod/s10/s10-stack/source/dts-platform-webapp/src/pages/workbench/index.tsx) 中的未使用 `toast` 阻断

## 完成标准

- [x] 可粘贴 ODS 字段列表并按顺序映射 Excel 列
- [x] 列数不一致时展示差异信息
- [x] 原有 ODS 表自动匹配能力不回归
