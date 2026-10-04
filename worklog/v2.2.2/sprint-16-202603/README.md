# Sprint-16: 数据接入 & 数据开发模块代码质量治理

**时间**: 2026-03
**状态**: DONE
**目标**: 修复 Critical/Important 级别代码质量问题，提升系统稳定性、可维护性和性能

## 背景

通过 Code Review 发现数据接入中心和数据开发中心两个模块存在以下结构性问题：
1. 前端多个巨石组件（SqlModelingPage 4340行、TransformCreatePage 2841行）
2. 后端上帝类（CatalogResource 3183行、ModelingSqlModelService 2867行）
3. 事务管理缺失/误用（ExcelImportService 无事务、CatalogResource 类级写事务）
4. 性能隐患（N+1 查询、findAll 无分页、逐行 save）
5. 代码重复（normalizeText 25+次、评分逻辑3处、轮询逻辑3处）

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 |
|----|---------|--------|---------|------|
| F1 | 后端安全/数据完整性修复 | P0 | 4 | DONE |
| F2 | 后端性能优化 | P1 | 4 | DONE |
| F3 | 前端巨石组件拆分 | P1 | 4 | DONE — TransformCreatePage 2841→1864行, SqlModelingPage 4340→3399行 |
| F4 | 前端代码卫生 | P2 | 4 | DONE |
| F5 | 后端结构重构 | P2 | 4 | DONE |

## Feature 详情

### F1: 后端安全/数据完整性修复 (P0)
- T01: ExcelImportService.loadProjectCockpitBatch 加 @Transactional
- T02: ExcelImportService:241 审计状态 Bug（SUCCESS/SUCCESS → SUCCESS/PARTIAL）
- T03: CatalogResource 移除类级 @Transactional，按方法标注 readOnly
- T04: ModelingSqlModelResource 移除类级 @Transactional

### F2: 后端性能优化 (P1)
- T05: ExcelImportService 逐行 save → saveAll 批量
- T06: CatalogResource.toDatasetDto N+1 → batch query
- T07: CatalogResource 治理路径 findAll → 分页/targeted query
- T08: DbtRunResultService manifest.json 解析加缓存

### F3: 前端巨石组件拆分 (P1)
- T09: TransformCreatePage 删除 ~1000 行重复 helpers，import from ingestionFormHelpers
- T10: TransformCreatePage 60 useState → useReducer/custom hook
- T11: SqlModelingPage 类型提取 → sqlModeling.types.ts
- T12: SqlModelingPage 子组件提取（Drawer/Modal/Panel）

### F4: 前端代码卫生 (P2)
- T13: normalizeText/formatTime 提取到 shared utils
- T14: renderStatus null-pointer 修复
- T15: Math.random() React key 替换
- T16: 统一 message vs toast 通知库

### F5: 后端结构重构 (P2)
- T17: CatalogResource 拆分为 5 个领域 Controller
- T18: ModelingSqlModelService 拆分为 3 个 Service
- T19: 数据源评分逻辑抽取共享方法
- T20: IngestionServiceClient exchangeTask/exchangeObject 合并

## 完成标准
- [ ] 所有 P0 问题修复，无数据丢失风险
- [ ] 关键 N+1 和 findAll 性能问题解决
- [ ] TransformCreatePage 降至 <1800 行
- [ ] SqlModelingPage 降至 <2500 行
- [ ] 编译通过，现有功能无回归
