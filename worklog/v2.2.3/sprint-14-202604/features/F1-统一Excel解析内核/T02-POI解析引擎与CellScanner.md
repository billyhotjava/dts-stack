# T02: POI 解析引擎与 CellScanner

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

用 `Apache POI` 负责 workbook 打开、sheet 选择、逐格扫描，替代当前平台侧对 EasyExcel 主数据读取的依赖。

## 设计要点

- `ExcelWorkbookGateway`：打开文件、枚举 sheet、按 sheetName/sheetIndex 选择目标
- `ExcelSheetScanner`：逐行逐格生成 `ExcelRowSnapshot`
- 对普通单元格、公式单元格、日期单元格统一进入 snapshot，而不是提前扁平化成字符串

## 完成标准

- [ ] 平台侧主链路不再用 EasyExcel 读取正文单元格
- [ ] 支持 sheetName 与 sheetIndex 双路径
- [ ] 现有 `.xlsx / .csv` 流程保持兼容
