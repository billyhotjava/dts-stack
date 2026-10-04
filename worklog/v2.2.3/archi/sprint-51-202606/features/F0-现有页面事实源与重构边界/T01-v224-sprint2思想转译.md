# T01: v2.2.4 Sprint-2 思想转译

**优先级**: P0  
**状态**: READY  
**依赖**: 无

## 目标

把 v2.2.4 Sprint-2 的 F1~F6 转译为 v2.2.3 现有页面可执行的重构方向。

## 技术设计

| v2.2.4 Feature | v2.2.3 转译 |
|----------------|-------------|
| 类型层补全 | 不新增 `src/v2/types`；在现有页面 DTO/API adapter 中确认 `sourceId`、`transformJobId`、tags、domain 是否可展示 |
| 字典域 | 复用现有标准管理、参考码、术语、连接器页面；抽前端共享查询前先确认重复请求和空态问题 |
| 血缘域 | 资产详情、血缘页、ETL/SQL 建模页增加可见交接点 |
| 全局 Store | 不重建 AppShell；优先页面级 query/hook 收敛 |
| 治理域 | 质量、授权、门禁信息进入资产详情和建模页 |
| 元数据与标签 | OpenMetadata 信息进入资产列表/详情，不只停留在技术页 |

## 影响范围

- 文档: Sprint-51 页面矩阵和 API 缺口表
- 暂不修改业务代码

## 验证

- [ ] 转译结果已写入 `assets/existing-page-cross-domain-matrix.md`
- [ ] 所有后续 Feature 都引用现有页面

## 完成标准

- [ ] 没有以“回植 `/src/v2`”作为任务路径。
