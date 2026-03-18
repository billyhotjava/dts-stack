# PM-001: 项目管理数仓模型适配客户真实数据

- **优先级**: P0
- **状态**: TODO

## 背景

客户实际 Excel 与 demo 测试数据差异大，需重新设计清洗逻辑。

## 子任务

### PM-001a: 维度关系重新设计
- 客户数据中"项目编号"= 项目名称，"分系统/分任务"= 子项目
- 两者是父子关系，需调整 enriched 模型的推导逻辑
- 学习 v2.2.1 的清洗模式，适配新的字段语义

### PM-001b: 脏数据清洗增强
- 子系统名含 `/` 分隔符（如 "三/电子设计"）— 不能简单按 `/` split
- 单元格含 Excel 计算引用（如 `A4-A3`）— dbt 层过滤或转换
- 单元格含错误值（如 `#value!`）— dbt 层替换为 NULL
- 原则：Addax 全部用 text 搬运，dbt 负责清洗

### PM-001c: 数据丢失排查
- 650 条 Excel 只有 350 条入库
- 排查 Addax 是否因为字段类型/格式问题跳过了行
- 确认 Addax job 配置是否用 text 类型搬运

### PM-001d: 重新生成模型 zip 包
- 修改后的模型 SQL 打包为新的 ui-import.zip 和 cli-deploy.zip
- 在本地和远程验证全流程

## 涉及文件

- `services/dts-dbt/models/dwd/` — 所有 DWD 模型
- `services/dts-dbt/macros/parse_date_safe.sql` — 日期解析宏
- `worklog/v2.2.2/dist/pm/` — zip 包

## 交付标准

- [ ] 客户 650 条 Excel 全部入库
- [ ] dbt build 后 21 张表正常生成
- [ ] 项目看板显示正确的项目/子项目数量
- [ ] 脏数据（公式、错误值）被清洗为 NULL，不阻断流程
