# PM-001e: Excel 公式与占位值清洗链路整改

- **优先级**: P0
- **状态**: TODO
- **关联任务**: `PM-001 项目管理数仓模型适配客户真实数据`

## 背景

当前项目管理 Excel 入湖链路实际是：

`Excel/WPS 文件 -> dts-platform ExcelImportService 解析成 data.csv -> Addax/Airflow 导入 PostgreSQL ODS -> dbt 清洗转换`

现场数据暴露出两类高频问题：

1. 部分单元格是 Excel 公式，导入结果不稳定，可能为空、旧值或未重算值。
2. 部分单元格用 `"/"` 表示“无数据/不适用”，如果后续被当作日期、数值或枚举直接处理，会导致落库失败或清洗结果异常。

## 现状问题

### 1. 公式值依赖 Excel 缓存结果

`ExcelImportService` 当前会把 Excel 解析成 `data.csv`，但未显式做公式重算，实际写入 CSV 的值依赖上传文件中已有的缓存结果。

影响：

- 用户如果上传的是程序生成或未重算保存过的 Excel，公式列可能为空或错误
- Addax 和 dbt 后续无法区分“真实空值”和“公式未求值”

### 2. 占位值 `"/"` 目前没有统一清洗规则

当前链路中，`"/"` 会原样进入 CSV 和 ODS。

影响：

- 日期列：`"/"` 无法按日期解析
- 数值列：`"/"` 无法转 numeric/double
- 枚举列：`"/"` 会污染口径统计

### 3. 文件型入湖仍存在按样例推断类型的风险

文件上传和 Addax 建表链路会参考样例值推断列类型，并可能把文件列建成 `date/double/boolean`，而不是统一按文本落库。

影响：

- 首行像日期、后续行却出现 `"/"` 或脏值时，可能在 ODS 导入阶段直接失败
- Addax 承担了不该承担的类型约束职责

## 目标

1. Excel 公式列在进入 `data.csv` 前得到稳定、可预期的取值。
2. `"/"`、空串、常见占位值不会阻断入湖和 dbt 构建。
3. 文件型 Addax 入湖只负责搬运原始文本，不负责强类型转换。
4. dbt staging 统一承担日期、数值、枚举清洗职责。

## 非目标

- 本任务不重做项目管理维度模型父子关系
- 本任务不改项目看板/大屏页面逻辑
- 本任务不改 Airflow 调度方式

## 方案

### PM-001e-1: 平台侧补齐公式求值

在 `ExcelImportService` 的 Excel 解析阶段，对公式单元格显式求值后再写入 `data.csv`。

要求：

- 对普通数值、日期、字符串公式输出稳定文本结果
- 对求值失败或错误公式，按空值处理并记录解析告警
- 继续兼容现有 `fillMerged/headerRow/dataStartRow` 逻辑

### PM-001e-2: 文件型入湖默认全部按文本落 ODS

调整文件上传与 Addax 建表策略：

- Excel/CSV 来源字段默认使用 `string/text/varchar`
- 不再基于样例值把文件列直接建成 `date/double/boolean`
- 保证 `"/"`、`#VALUE!`、空串、异常日期文本都能先进入 ODS

原则：

- Addax 只搬运
- ODS 保留原始值
- 类型转换放到 dbt

### PM-001e-3: dbt 增加统一占位值清洗宏

在 `services/dts-dbt/macros/` 增加统一清洗宏，例如：

- `nullif_placeholder(expr)`
- `parse_numeric_safe(expr)` 或等价处理

并改造现有 `parse_date_safe.sql` 与项目管理相关 staging/DWD 模型：

- `''`、`'/'`、`'-'`、`'--'`、`'N/A'`、`'#VALUE!'` 等占位值统一转 `NULL`
- 日期列经清洗后再走 `parse_date_safe`
- 数值列经清洗后再转 numeric/double
- 风险等级、完成状态等枚举列决定是转 `NULL` 还是保留原文兜底

### PM-001e-4: 建立回归测试

需要补齐至少三类测试：

1. Excel 中包含公式单元格，解析后 `data.csv` 输出为求值结果
2. Excel/CSV 中包含 `"/"`、`#VALUE!` 等占位值，入湖阶段不报错
3. dbt 清洗后，日期/数值字段正确转 `NULL` 或合法值

## 涉及文件

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/ExcelImportService.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/infra/`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/FileUploadService.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/AddaxJobService.java`
- `source/dts-ingestion/src/test/java/com/yuzhi/dts/ingestion/service/etl/AddaxJobServiceTest.java`
- `services/dts-dbt/macros/parse_date_safe.sql`
- `services/dts-dbt/macros/` 下新增占位值/数值清洗宏
- `services/dts-dbt/models/dwd/prjtest1/`
- `services/dts-dbt/models/dws/prjtest1/`

## 风险点

- Excel 中存在复杂公式或外部引用时，公式求值可能无法完全还原 Excel/WPS 结果
- 统一把文件列落成文本后，部分依赖 ODS 强类型的旧逻辑需要同步检查
- `"/"` 在个别字段中可能是合法业务字符，清洗规则必须限定在目标字段语义内

## 交付标准

- [ ] 上传含公式单元格的 Excel，`data.csv` 中落的是公式结果，不是空值或公式文本
- [ ] 上传含 `"/"`、`#VALUE!`、空串的 Excel/CSV，Addax 入湖不因类型问题失败
- [ ] 文件型 ODS 目标表默认按文本字段建表，不再因样例推断导致 `date/double` 强约束
- [ ] dbt 对日期/数值/枚举字段完成统一清洗，构建过程不被占位值阻断
- [ ] 补齐平台侧、ingestion 侧、dbt 侧回归验证

## 备注

该任务完成后，Excel 入湖链路应固定为：

`Excel 解析求值 -> CSV 原始文本落湖 -> dbt 统一清洗转换`

后续如需支持更多占位值或字段级规则，应继续在 dbt 清洗层扩展，不再把业务清洗逻辑下沉到 Addax。
