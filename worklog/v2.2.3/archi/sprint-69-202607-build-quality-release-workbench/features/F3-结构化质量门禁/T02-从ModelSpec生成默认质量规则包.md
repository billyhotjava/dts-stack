# T02：从 ModelSpec 生成默认质量规则包

**优先级**：P0
**状态**：READY
**依赖**：T01

## 目标

依据模型类型、键、粒度、标准绑定和字段约束生成可解释的默认规则包，并允许引用治理质量模块的增强规则。

## 技术设计

- FACT 默认覆盖粒度唯一性、主键/外键、必填、数值域和来源完整性。
- DIMENSION 默认覆盖业务键、代理键、层级、SCD 有效期与 current flag；最终 fixture 等待 Sprint-67。
- SUMMARY/APPLICATION 默认覆盖主键、聚合一致性、关键字段和新鲜度。
- 规则正文由质量 owner 管理；交付侧只固定 ruleId/version/参数。

## 影响范围

- 新增 `ModelQualityRulePackFactory.java`
- ModelSpec/quality 适配端口
- 模型类型 fixture 与单元测试

## 实施步骤

1. 先写四类模型规则快照测试及非法字段组合负例。
2. 实现纯函数规则包生成器和稳定排序。
3. 对 DIMENSION fixture 设置显式 Sprint-67 稳定接口依赖。

## 完成标准

- [ ] 每条默认规则能解释由哪个模型声明生成。
- [ ] 规则生成稳定、可版本化，不把页面文案当规则真值。
- [ ] **UI 契约验收**：规则包预览按 FACT、DIMENSION、SUMMARY、APPLICATION 分组，展示规则来源、版本和生成原因，业务用户能理解“为什么检查”。
