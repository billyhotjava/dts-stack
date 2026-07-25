# T05：接收 dbt ZIP 并转换为内部模型包

**优先级**: P0  
**状态**: IN_PROGRESS  
**依赖**: T01、T02

## 目标

把普通建模的用户输入从 JSON 调整为既有 dbt ZIP，由服务端安全、无副作用地转换为内部 `dts.model-package/v1`。

## 技术设计

- 增加认证的 multipart archive inspect API，单文件上限 32 MiB。
- 支持根目录或单层项目目录中的 dbt artifact：`manifest.json`/`target/manifest.json`、可选 catalog/schema YAML、SQL、macro 和 seed。
- 兼容高级建模的 `models.tsv + SQL` ZIP；复用其文件约定，但不调用会写旧 SQL 工作区的 batch-import。
- ZIP 解包必须限制路径、条目数、单项和总大小，拒绝重复路径、嵌套归档和危险路径，并保证临时目录清理。
- 转换结果只作为浏览器同源流程和服务端预检的内部中间态；保留原 JSON API 供自动化兼容。
- legacy ZIP 缺少模型类型、粒度、业务域、来源或可靠依赖时返回稳定 issue/阻断，不根据名称或 SQL 猜测。

## 影响范围

- dbt archive 安全读取与格式识别服务。
- `ModelSpecImportResource` archive inspect 入口。
- archive 转换、异常语义与资源清理测试。

## 验证

- [ ] artifact ZIP 可确定性转换，内部 checksum 可重复。
- [ ] legacy `models.tsv + SQL` 可识别；缺失语义不会被静默补造。
- [ ] 坏 ZIP、路径穿越、重复路径、超限和缺失 SQL 返回稳定错误。
- [ ] inspect 不写 ModelSpec、implementation、artifact 或旧 dbt 工作区。

## 完成标准

- [ ] 页面不再要求用户准备 JSON。
- [ ] 临时文件始终清理，内部 JSON 契约和现有 preview/apply 保持兼容。
