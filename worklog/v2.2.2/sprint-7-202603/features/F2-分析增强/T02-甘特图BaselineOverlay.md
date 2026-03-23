# T02: 甘特图 Baseline Overlay

**优先级**: P1
**状态**: READY
**依赖**: 无

## 目标
甘特图为每个任务并排显示计划 baseline（灰色细条）和实际进度（彩色粗条），直观展示计划偏差

## 技术设计

### 1. 数据层
任务数据中已有 `planStartDate`、`planEndDate`、`actualStartDate`、`actualEndDate`，无需后端改动。

### 2. ProjectGanttBoard 改造
当前每个任务渲染一条 bar，改为渲染两条叠加 bar：
- **Baseline bar**（下层）：灰色 `#E5E7EB`，高度 8px，表示计划时间段
- **Actual bar**（上层）：彩色（红/黄/绿），高度 14px，表示实际时间段
- 两条 bar 垂直居中对齐，baseline 在下方露出部分
- 无实际日期时，actual bar 用虚线边框表示进行中

### 3. 偏差标注
- 若实际结束 > 计划结束，在 bar 右侧标注红色 `+N天`
- 若实际结束 < 计划结束，标注绿色 `-N天`

## 影响范围
- `components/ProjectGanttBoard.tsx` — 双 bar 渲染 + 偏差标注

## 验证
- [ ] baseline 灰色条与 actual 彩色条并排显示
- [ ] 延期任务右侧显示红色 +N天
- [ ] 提前完成任务右侧显示绿色 -N天
- [ ] 进行中任务 actual bar 用虚线边框

## 完成标准
- [ ] 视觉效果清晰，两条 bar 不互相遮挡
- [ ] 无 actual 数据时仅显示 baseline
