# dbt 导入、发布与验收手册

## 1. 两条路径必须得到同一套模型结果

当前 PJM 的 STG、DWD、DWS、ADS SQL 已经存在于 dbt 项目中。本次验收同时覆盖：

- 在模型工作台按依赖顺序逐个创建业务模型，并绑定同一 dbt 实现；
- 在逆向建模中导入完整 dbt ZIP，由制品恢复模型、字段、依赖和实现。

两条路径必须收敛到相同的 43 个业务模型、20 个技术节点、依赖图、物理关系和资产。手工路径不是只填逻辑字段；没有实现绑定、上游修订和物理关系的草稿不计入交付。指标在业务模型上线后从指标工作台创建，不由 dbt 导入器代建。

## 2. Step 1：准备 dbt ZIP

先在已验证测试库刷新 `target/manifest.json` 与 `target/catalog.json`，再在 `dbt_model` 根目录执行可复现打包脚本：

```bash
cd worklog/v2.2.3/s10/v4/pjm/dbt_model
./scripts/build_import_zip.sh
```

打包检查：

- 包含 `dbt_project.yml`、`models/`、`macros/`、`tests/`、`package-contract.yml`。
- 必须包含与源码同一次解析生成的 `target/manifest.json` 和 `target/catalog.json`；缺少它们时，含宏、hook 和 `ref/source` 的项目不能按源码文本无损恢复。
- 包含 `docs/metric-handbook.md` 和 `docs/metric-registry.json`，用于校验 77 个稳定代码。其中 76 个是可在指标工作台登记的指标定义，`pjm_qual_count` 是随图表分组变化的上下文辅助序列，不得伪造成独立指标；这些文件不是自动导入指标的承诺。
- 不包含 `profiles.yml`、数据库密码、令牌、证书或 `.env`。
- 不把 `target/index.html`、`target/run_results.json`、日志、`dbt_packages` 等运行态或无关大文件打入包。
- 10 张 STG、8 张 canonical 维度、10 张 alias、10 张 DWD、10 张 DWS、15 张 ADS 均在包内。
- `package-contract.yml`、`manifest.json`、`catalog.json` 的模型、字段和测试数量一致；任何缺项都视为 ZIP 不完整。

逆向建模的第一步只检查包，不执行其中 SQL 或宏。

## 3. Step 2：开始逆向建模

1. 登录 DTS，进入“数据建模 → 逆向建模”。
2. 点击“快速开始”。
3. 选择 `worklog/v2.2.3/s10/v4/pjm/pjm-dbt-model.zip`。
4. 点击“开始识别”。
5. 查看三类结果：包结构检查、导入投影、dbt 物化。
6. 记录“可导入、待补充、阻断、技术节点”数量；有阻断时先处理，不直接生成。

## 4. Step 3：确认规划、域和来源映射

在“检查报告与模型映射”页：

1. 选择本次已确认的 PJM 规划上下文。
2. 将包内数据域映射到当前规划稳定 ID：

| 包内语义 | DTS 数据域 |
|---|---|
| project/progress/risk | 研究项目域 |
| quality | 质量管理域 |
| tech-state | 产品技术域 |
| budget | 财务管理域 |
| material | 物料供应域 |

3. 将 10 张 dbt source 映射到 DTS 当前来源绑定。当前主链至少必须映射 5 张核心来源。
4. 不使用表名猜测一个新数据域；映射目标必须是前一步已确认的数据域 ID。
5. 重新导入时，只有确实发生 dbt `unique_id` 重命名才填写 old→new 映射。

当前 dbt 项目已在模型 `meta.dts` 中提供 `domainCode`、模型类型、层次、粒度、字段角色及模型类型专属语义。页面仍必须把稳定代码映射到目标环境中的真实规划对象；映射失败时不得按名称新建或按默认值批量通过。

## 5. Step 4：逐模型确认导入语义

选择模型时执行以下分类：

| dbt 对象 | DTS 处理 |
|---|---|
| `stg_pm__*` | 技术节点/DBT 实现，不创建为业务 FACT |
| `dim_*_v2` | DIMENSION / DWD；需绑定已确认维度定义 |
| `dim_*_alias` | DWD 技术辅助模型，不新建同名业务维度概念 |
| `biz_dwd_*` | FACT / DWD；填写本手册定义的粒度和业务键 |
| `biz_dws_*` | SUMMARY / DWS；固定 DWD 上游 |
| `biz_ads_*` | APPLICATION / ADS；固定 DWS/ADS 上游 |

页面可补录的导入语义包括“业务名称、模型类型、目标分层、粒度说明、业务主键、发布密级和规划归属”。逐张对照 02～05 手册填写。发布密级可以由有权操作者批量确认，但系统不得从测试数据、文件名或 ODS `classification` 字段推断。

包内已经提供事实形态、时间语义和维度定义稳定代码。业务过程、数据集市、主题域和字段密级属于目标环境治理事实，必须在预览上下文中映射到现有对象或由有权操作者显式确认；不得为了绕过映射把 FACT 改成 SUMMARY、把 APPLICATION 改成普通汇总表，或把维度表改成普通应用表。

## 6. Step 5：生成预览并应用

1. 只勾选本批确认的模型。
2. 为全部已选模型确认发布密级；为每张 FACT 选择同域已确认业务过程，为每张 APPLICATION 选择当前数据集市和所属主题域。
3. 点击“生成预览”。
4. 对每项查看 `CREATE/UPDATE/SKIP/CONFLICT/BLOCKED` 和 `DESIGNER_GENERATED/DBT_BACKED/BLOCKED`。
5. 本项目已有 SQL 的业务模型应形成 `DBT_BACKED` 实现；出现 `DESIGNER_GENERATED` 时先确认是否丢失了 dbt 实现证据。
6. 冲突项逐项选择保留当前或接受导入，不进行全局盲选。
7. 预览无未解释阻断后点击“生成模型”。
8. 生成只创建/更新草稿，不会自动发布和物化。
9. 结果为 PARTIAL/FAILED/BLOCKED 时，只重试服务端标记为可重试的对象；语义错误必须修复后重新预览。
10. 需要撤销时使用“前向撤销本次导入”，它追加恢复修订而不是删除历史。

## 7. Step 6：在模型工作台补录和核对

导入完成后点击“进入模型工作台”，按依赖顺序处理：

1. 8 张 canonical 维度表；
2. alias 技术辅助表；
3. 10 张 DWD FACT；
4. 10 张 DWS SUMMARY；
5. 15 张 ADS APPLICATION。

每个模型检查：

- 规划、数据域、模型类型和层次正确；
- 物理表名与 dbt 一致；
- 模型粒度和 KEY 一致；
- 每个字段有中文显示名；
- DWD 来源和维度引用、DWS/ADS 上游都固定到修订；
- 实现所有权为预期的 DBT 管理模式；
- `dbtUniqueId`、实现修订和校验和存在；
- 没有漂移或已删除上游。

字段技术名必须匹配 `^[a-z][a-z0-9_]{0,62}$`。页面允许的常用类型为 `STRING/BOOLEAN/INT/BIGINT/DECIMAL/DATE/TIMESTAMP`。

## 8. Step 7：提交逻辑设计

1. 保存当前模型修订。
2. 点击“提交”。
3. 查看目标阶段 `DESIGNED`，只处理逻辑设计阻断。

| 模型类型 | DESIGNED 必须满足 |
|---|---|
| 全部 | 粒度键唯一命中 KEY；字段技术名合法；每个字段有中文显示名 |
| DIMENSION | 维度定义/描述、维度 profile、SCD 策略、至少一个 KEY |
| FACT | 真实业务过程、事实形态、兼容时间语义，时间语义引用 TIME 字段 |
| SUMMARY | 至少一个 MEASURE 或指标引用 |
| APPLICATION | 至少一个输出字段；当前前端还要求至少一个 KEY |

“提交检查通过”只表示逻辑设计可继续，不代表已构建、测试或发布。

## 9. Step 8：验证 dbt 实现

在可访问目标 profile 的安全环境中，按项目说明执行：

```bash
cd worklog/v2.2.3/s10/v4/pjm/dbt_model
dbt deps
dbt run --profile dts
dbt test --profile dts
```

执行前确认 profile 指向测试环境，不在不明目标直接运行。重点检查：

- 10 张 STG 行级对应 ODS，日期/数字异常不会让全批失败；
- 8 张维度稳定 ID unique/not_null；
- 10 张 DWD 主键、必要业务字段和 accepted values；
- 10 张 DWS 组合粒度唯一、合计可与 DWD 对账；
- 15 张 ADS 的比率单位、金额单位和分子/分母一致；
- 预算范围键、质量环境/TOP1 字段、候选自然键和业务快照日期已有明确结论。

本地 `dbt run/test` 成功不自动成为 DTS 发布证据。DTS RELEASE_READY 要求构建和测试结果绑定同一 ModelSpec 修订、实现修订和校验和。

## 10. Step 9：发布与物化

1. 在模型工作台检查“实现”不再显示“尚无”。
2. 对 DBT 管理模型进入“模型开发”，确认高级 dbt 草稿、文件、校验和和提交状态。
3. 点击“发布”，进入“发布与物化”。
4. 先选择开发/测试环境完成构建和测试，不直接选择生产环境。
5. 创建并锁定只包含本批依赖闭包的候选。
6. 查看 RELEASE_READY 门禁，全部 READY 后填写发布说明。
7. 点击“发布”。
8. 在“生命周期日志、版本证据、发布记录”中核对结果。

RELEASE_READY 主要证据：

- 当前来源、上游模型和维度修订未漂移；
- 字段标准覆盖符合规划策略；
- 质量测试通过；
- 字段权限分级完成；
- 模型/资产密级满足分类分级发布门禁；
- SQL/SCHEMA（以及需要时 TEST）制品齐全；
- 编译和测试事件属于当前模型及实现校验和。

源表 `classification` 不会自动等于模型最终密级。密级证据由分类分级治理流程提供，实施人员不得自行降低或代填。

## 11. 验收分层

最终报告必须分开写：

| 验收层 | 可证明内容 |
|---|---|
| 文档/源码核对 | 手册与当前 DDL、dbt SQL、DTS 表单和门禁一致 |
| dbt 本地/测试库 | SQL 可运行、测试通过、数据口径对账 |
| DTS API/导入 | 模型、实现、依赖和修订实际写入服务端 |
| 部署健康 | 目标容器已更新，服务和迁移健康 |
| 真实账号 UI | 从真实菜单点击完成规划、导入、提交、发布并核对审计 |

没有后两项证据时，只能写“代码/本地验证完成”，不能写 `REAL/DELIVERED`。

## 12. 最终验收清单

- [ ] 10 张 ODS source 全部可追溯且固定版本有效。
- [ ] 8 个维度定义和维度表绑定正确，alias 表不冒充业务维度。
- [ ] 10 张 DWD、10 张 DWS、15 张 ADS 数量和依赖与本手册一致。
- [ ] 63 个 dbt 节点被正确分类为 43 个业务 ModelSpec 与 20 个技术节点。
- [ ] 10 张 FACT 均绑定同域已确认业务过程；15 张 APPLICATION 均绑定当前数据集市和所属主题域。
- [ ] DESIGNED、IMPLEMENTATION_READY、RELEASE_READY 分阶段证据均可查看。
- [ ] 所有模型和实现证据属于当前修订/校验和。
- [ ] 密级、权限和字段标准由有权流程确认。
- [ ] 测试环境数据对账通过；目标部署和真实菜单旅程另有证据。
