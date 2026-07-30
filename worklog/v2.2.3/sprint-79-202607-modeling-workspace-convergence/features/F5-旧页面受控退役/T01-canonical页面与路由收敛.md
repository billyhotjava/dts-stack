# T01：canonical 页面与路由收敛

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F1～F4

## 目标

抽取业务面板后删除重复页面布局，并让旧 canonical 深链带上下文进入统一工作台。

## 技术设计

- **映射**：plans→`module=planning`；dimensions/models→`module=models` + asset；metric-workbench→`module=metrics`。
- **参数**：保留 `planId,modelSpecId,domainId,revision,returnTo`，非法外部 returnTo 丢弃。
- **输出**：replace redirect，避免浏览器返回循环。
- **删除**：只有在面板已由 Shell 消费且 route source-contract 通过时删除旧 Page shell。
- **错误路径**：无法解析 model/object 时进入显式 recovery，不静默跳首页。
- **菜单**：dts-admin 菜单种子收敛到 workbench；不删除原 menu id/role binding，先软删除。

## 验证

- [ ] 每条旧 deep link 正反向参数表测试。
- [ ] 收藏、帮助链接和动态 resolver 无悬挂。
- [ ] Chrome95 返回键/刷新不循环。

## Definition of Done

- [ ] IT-08 canonical 段通过。
- [ ] 页面布局代码不再重复，专业 SQL/dbt 路由仍可达。
