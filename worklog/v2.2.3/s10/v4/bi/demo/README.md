# BI Demo 目录

本目录用于放置驾驶舱大屏 HTML 原型和视觉验证文件。

## 命名建议

- `overview.html`: 领导层总览。
- `delivery-board.html`: 交付执行中层看板。
- `risk-board.html`: 风险预警中层看板。
- `quality-board.html`: 质量闭环中层看板。
- `tech-change-board.html`: 技术状态与变更中层看板。
- `detail-template.html`: 底层明细页模板。

## 当前样例

新建驾驶舱套件入口：

- `index.html`: 单文件多视图 HTML 原型，包含 L0 领导总览、L1 交付/风险/质量/技术变更专题、L2 明细模板。

当前检测到已上传的参考样例位于 `../../pjm/bi/demo/preview1.html`。如果需要把它纳入根级 `bi/demo`，建议后续复制为 `reference-light-enterprise.html`，避免与新原型命名冲突。

## 原型约束

- 优先使用单文件 HTML，方便评审和截图。
- CSS token 需要和 `dashboard-series-design.md` 中的视觉方向保持一致。
- 原型数据可以先静态写死，但字段命名应靠近后续指标口径。
- 每张页面都必须说明它属于 L0、L1 还是 L2。
