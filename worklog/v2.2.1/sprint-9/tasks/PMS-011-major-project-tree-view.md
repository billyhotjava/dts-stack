# PMS-011

## 标题

实现重大项目树视图与树状进度看板组件，突出 `重大项目 -> 子项目 -> 节点` 的核心专题能力。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/project-cockpit/views/`
- `source/dts-analytics-webapp/modern/src/pages/project-cockpit/components/`

## 目标

- 让用户快速识别哪个子项目拖住主计划、卡在什么节点

## 交付

- 树状进度看板主组件
- 当前选中对象详情面板
- 子项目对比区块
- 关键节点时间链 / 甘特

## 验收

- 支持三层展开：重大项目、子项目、节点
- 每层都可展示进度、风险、延期和聚合计数
- 选择树节点后，右侧详情和下方图表会同步切换

## 当前进度

- 状态：DONE

## 风险

- 若树节点信息过于稀薄，会丢失这张主屏的核心价值
