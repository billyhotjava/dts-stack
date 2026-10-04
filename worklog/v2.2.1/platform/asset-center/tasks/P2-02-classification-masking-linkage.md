# P2-02 分级分类与脱敏规则联动

`status`: `done`
`priority`: `P2`

## 完成内容

### 后端

1. 新增分类映射预检接口：`POST /api/catalog/classification-mapping/validate`。  
   支持重复键、非法密级、空字段校验；返回 `conflicts/warnings` 与操作建议。
2. 新增联动查询接口：`GET /api/catalog/classification-masking/linkage`。  
   - `datasetId` 模式：返回资产“当前密级 -> 生效脱敏规则 -> 冲突提示”。  
   - summary 模式：返回密级分布、脱敏覆盖与缺口统计。
3. `PUT /api/catalog/classification-mapping` 增加保存前一致性校验。  
   有冲突直接阻断并返回可操作提示；无冲突时按规范化后的映射入库。

### 前端

1. 数据治理中心“分类映射”新增“冲突预检”按钮，展示冲突/告警及建议。
2. 资产门户详情页“治理状态”新增“密级与脱敏联动”卡片，展示：
   - 当前密级
   - 生效规则数量与明细
   - 冲突提示与修复建议
3. 数据治理页与资产页增加轻量同步刷新机制：  
   通过 `localStorage + window event` 通知，在修改映射/规则后资产页自动刷新联动状态。

## 验收结果

- 分级分类变更可触发规则一致性校验。
- 冲突可在前端预检阶段发现并给出修复建议。
- 资产详情可直接观察密级与脱敏策略联动状态。
