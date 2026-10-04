# dbt Package Platform Gaps

## P0

### 1. 去掉逻辑建模页面加载时的工作区自动扫描

现状：

- `listSqlModels()` 会在后端隐式调用 `syncWorkspaceModels()`。

影响：

- 现场只要打开 `逻辑建模` 页面，就可能把 dbt 工作区变化写回平台元数据。

要求：

- 页面加载不得隐式同步
- 如仍保留该能力，必须改成显式 API 或显式按钮

### 2. 增加 `modeling_plan` 同名校验

现状：

- `项目空间` 可重复创建同名记录
- 前端目录树按原始 plan 列表直接展示

影响：

- 同名 plan 难以区分，易造成“重复项目节点”错觉

要求：

- 创建/更新时增加运行时同名校验
- 数据库唯一索引作为后续增强项评估

### 3. 修正 dbt 状态流转

现状：

- `dbt run/build/test` 触发后就更新 `PUBLISHED/TESTED`

影响：

- web 显示“已上线”，但目标表可能未生成

要求：

- 至少先移除 trigger 后的乐观状态更新
- 后续再改成成功结果驱动

### 4. 收敛 source 定义单一权威

现状：

- `ods_sources.yml` 与项目管理专用 source 文件并存
- ODS 映射记录还可能把相同 table 重复写入 `ods_sources.yml`

影响：

- dbt compile/run 可能因为 source 或 table 重复失败

要求：

- 只保留一套 source 权威来源
- `ods_sources.yml` 生成时按 `(schema, table)` 去重

## P1

- `dts-reset` 改为 package-aware
- 建立 package registry 与 manifest 一致性
- 提供显式 register API/CLI

## P2

- 重新定义 `项目空间` 的产品定位
- 统一 dbt / card / sql / template 的离线交付策略
