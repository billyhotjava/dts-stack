# T06：统一普通与高级建模 dbt ZIP 契约

**优先级**: P0  
**状态**: IN_PROGRESS  
**依赖**: T05

## 目标

让同一个标准 dbt 项目 ZIP 同时成为普通建模的可视化投影输入和高级建模的 SQL 工程输入；现有 `pjm-dbt-model.zip` 缺少预生成 artifact 时也能进入普通预检，不再整体标记为 legacy。

## 技术设计

- 统一交付根为 `dbt_project.yml + models/**/*.sql + schema YAML + macros`；兼容可选 `models.tsv` 和 `target/manifest.json + catalog.json`。
- archive inspect 先读取已有 artifact；没有 artifact 时，使用有文件、节点、边、深度和遍历预算上限的静态解析器读取 `dbt_project.yml`、SQL、显式配置及可选 `models.tsv`。该路径不调用 dbt，不执行 SQL，不写目标数据库或高级 dbt 工作区。
- 结构性事实只来自可证明的项目路径、materialization、显式 tags、字面量 `ref()` / `source()` 和 `models.tsv` 清单；本最小实现不声称解释任意 Jinja、宏展开或 schema YAML 业务语义。粒度、SCD、消费场景等无法证明的内容生成逐模型待确认 issue，不能静默补造，也不能阻断整个项目进入上下文确认。
- 普通入口输出内部 `dts.model-package/v1`；高级入口继续保存原始工程。两个入口共享 ZIP 安全门禁和项目根识别，不共享会写高级工作区的导入副作用。
- UI 展示统一“dbt 项目 ZIP”，技术详情说明 artifact 是系统可补齐的解析缓存；失败时返回具体解析阶段、节点和修复建议。

## 影响范围

- dbt archive 项目识别、artifact 获取和内部模型包转换。
- 普通导入向导的包类型、错误和待确认呈现。
- 高级 ZIP 入口的兼容回归测试。
- Sprint-70 PJM 真实双入口证据。

## 验证

- [ ] 原始 `worklog/v2.2.3/s10/v4/pjm/pjm-dbt-model.zip` 不修改内容即可由普通 archive inspect 识别为 dbt 项目，而不是 `legacy`。
- [ ] 普通入口生成稳定依赖图、来源候选和逐模型语义问题；不运行 SQL、不写目标库或高级工作区。
- [ ] 同一文件仍可由高级 ZIP 入口导入 SQL/YAML/macro。
- [ ] 对静态解析器可证明的字面量节点，artifact 与无 artifact 两条路径生成相同的稳定 node identity 与依赖关系；动态 Jinja/宏依赖必须显式标记待确认，不能伪造边。
- [ ] 非法 UTF-8、越界路径、超限项目、动态引用和缺失 SQL 返回稳定诊断或逐模型 issue，不退化为笼统 `MODEL_IMPORT_REQUEST_INVALID`。

## 完成标准

- [ ] 一个 ZIP、两个入口、一个项目身份；普通模式和高级模式不再要求用户准备不同交付物。
- [ ] Chrome 95 使用原始 PJM ZIP 完成普通预检，并保留高级导入兼容证据。
