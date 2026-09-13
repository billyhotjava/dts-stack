# F2: 清洗函数库（后端）

**优先级**: P0
**状态**: READY

## 目标
扩展清洗函数库，覆盖 Excel 入湖常见的数据清洗场景（日期标准化、单位剥离、枚举映射、空值填充）。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 清洗函数表与初始化 | P0 | READY | - |
| T02 | 清洗执行引擎 | P0 | READY | T01 |
| T03 | 新增清洗函数（DATE_NORMALIZE/STRIP_UNIT/ENUM_MAP/FILL_DEFAULT） | P0 | READY | T01 |
| T04 | 入湖自动触发链路 | P1 | READY | T02 |

## 完成标准
- [ ] GovCleansingFunction 表含 TRIM/UPPER_CASE + 4 种新增函数
- [ ] DataCleansingService 可链式执行多个函数
- [ ] DATE_NORMALIZE 能处理多种日期格式统一为 yyyy-MM-dd
- [ ] 入湖完成后自动触发清洗
