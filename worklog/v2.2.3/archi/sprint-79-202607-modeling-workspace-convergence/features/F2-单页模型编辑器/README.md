# F2：单页模型编辑器

**优先级**：P0  
**状态**：IN_PROGRESS（T01/T04 主线严格只读验收通过；T02/T03 的代表数据写入旅程待补）

## 目标

用户在对象树右侧一次完成业务维度、四类 ModelSpec 的基础信息和字段设计，不再穿行于三张解释性阶段页。

## 契约

| 类型 | 契约 | 要点 |
|---|---|---|
| 业务维度 | DimensionDefinition CRUD/confirm | systemCode 服务端生成；属性无 dataType |
| 逻辑模型 | ModelSpec CRUD/stage-gates | DIMENSION/FACT/SUMMARY/APPLICATION 不变 |
| 字段关联 | ModelSpec fields + dimension/standard/metric refs | 主键字段 role=KEY；维度属性编码精确映射 |
| UI 状态 | `panel=base|fields|relations` | 仅视图状态；逻辑/实现/发布门禁仍由服务端返回 |

## UI/UX

```text
┌ 模型标题 / 类型 / 状态 ───────── [保存] [校验] [发布] ┐
│ 基础信息                                               │
├ 字段表：编码｜名称｜类型｜作用｜维度属性｜标准｜非空     │
│ [+字段] [从来源导入] [显示设置] [字段关联]              │
├ 分区与数据实现（抽屉）  关系/质量/日志（抽屉）           │
└ 发布前阻断集中面板                                     ┘
```

保存只校验当前逻辑设计；未来阶段要求不计入当前红色错误。错误必须定位到具体字段行。

## Tasks

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 重组模型详情为单页画布 | PASS_WITH_GAPS | F1/T01 |
| T02 | 收敛业务维度与字段映射 | IN_PROGRESS | T01 |
| T03 | 补齐四类模型与关联抽屉 | IN_PROGRESS | T01、T02 |
| T04 | 模型对象上下文接入 | PASS_WITH_GAPS | F1/T02、T01 |

## Definition of Ready

- [x] ModelSpec/Dimension 现有 API 和字段 owner 已冻结。
- [x] F0 登录基线通过。
- [x] `ModelSpecDetailPage`、字段组件影响分析完成。

## 完成标准

- [ ] IT-02、IT-03 在正式 DTS 中通过。
- [x] stage gate 语义和 CAS 未改变。
- [x] 字段错误可定位，不再只有页面顶部笼统报错。
