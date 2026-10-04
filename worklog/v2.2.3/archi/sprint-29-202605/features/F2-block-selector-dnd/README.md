# F2: BlockSelector 节点库 + CandidateNode 拖拽体验

**优先级**: P0
**状态**: DONE
**依赖**: F1（画布壳）

## 目标

抄 Dify `block-selector/` + `candidate-node.tsx`：左侧持久面板 + 画布上 + 号 popover 双入口；拖拽时显示候选虚影；落点自动连边。这是 Dify 编辑器最直观的"专业感"来源。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | `BlockSelectorPanel` 左侧持久面板：分类 + 搜索 + 拖出 | P0 | DONE | F1-T02 |
| T02 | `BlockSelectorPopover` 节点 + 号弹窗：与面板共用 blocks.config | P0 | DONE | T01 |
| T03 | `CandidateNode`：拖拽中虚影节点（dataTransfer + ghost） | P0 | DONE | T01 |
| T04 | 落点自动连边：从源节点 + 号拖出时 onDrop 自动 addEdge | P0 | DONE | T03 |

## 完成标准

- [x] `blocks.config.tsx` 单一数据源驱动 Panel；T02 Popover 继续复用同一份配置
- [x] 从面板拖一个节点到画布：CandidateNode drag image 跟随光标 → 落下后正常出现节点
- [x] 在已存在节点的 + 号上拖出新节点：自动创建连接边（source → 新节点 target）
- [x] 拖拽中按 ESC 取消：未触发 drop 时不创建节点、无连线残留
- [ ] 真机 Chrome 95：dataTransfer + drag image 不崩（待整体冒烟）
