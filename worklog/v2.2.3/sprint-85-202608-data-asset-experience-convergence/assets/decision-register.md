# Sprint-85 决策登记（Decision Register）

| ID | 决策 | 理由 | 状态 |
|---|---|---|---|
| D01 | 资产地图更名/定位为"资产概览"仪表盘；血缘图谱仍属"血缘与影响分析" | 统计矩阵有治理价值；问题是命名与导航 | ACCEPTED |
| D02 | 台账/搜索以 assets-v2 为唯一事实源；legacy 仅深链兼容读取 | 消除双轨字段/过滤漂移 | ACCEPTED |
| D03 | 详情页以 assets-v2 为唯一渲染事实源；legacy 仅 legacyId 解析 | 同 URL 双页面是最大困惑源 | ACCEPTED |
| D04 | 血缘展示只保留 DTS 本地影响链；OM 缓存降级为同步证据说明 | 两套血缘并存语义混乱 | ACCEPTED |
| D05 | 血缘节点身份稳定化（作业/源节点可复现 id；列节点规范 id）；关键词即过滤 | 深链/导出/会话一致的前提 | ACCEPTED |
| D06 | 权限申请统一入口：台账行级、详情 Tab、概览卡均深链 `?action=new&assetId=`；审批页统一命名；我的授权/审计侧边可达 | 四处功能保留、入口统一 | ACCEPTED |
| D07 | 统一筛选协议：URL query + 单一 localStorage key；血缘筛选 URL 化 | 消除跨页漂移 | ACCEPTED |
| D08 | 血缘契约类型下沉到共享契约模块（`features/catalog/lineageContracts.ts`） | 解除组件→页面反向依赖 | ACCEPTED |
| D09 | 旧 `AssetDetailPage` 与 `/catalog/asset-detail` 收敛（410 + 重定向到详情页） | 双详情页并存是认知负担 | ACCEPTED |
| D10 | 本 Sprint 不动后端血缘引擎/权限模型/goldenchain/多租户；只收敛表面与导航 | 范围控制、交付闭环 | ACCEPTED |
| D11 | 后端 `CatalogDatasetResource` legacy 数据集 CRUD 保留（外部/老接口消费），前端不再新建消费方 | 退役只做前端收敛，后端按 caller=0 再议 | ACCEPTED |
