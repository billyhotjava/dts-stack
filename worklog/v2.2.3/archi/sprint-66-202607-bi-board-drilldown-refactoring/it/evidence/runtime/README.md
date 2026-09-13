# 运行时矩阵证据

`drillRuntime.test.ts` 使用同一组 `sourcePath -> variableKey` 映射遍历 SQL、API、Card、Dataset、Metric，五类目标均生成 `{ selectedKey: "A-01" }`，由 `DataLayer` 统一合并现有绑定参数并交给 `useCardDataSource`。

已验证：

- `inheritContext=true` 合并父层参数，同名目标以后层覆盖；`false` 隔离父层。
- 上卷裁剪 stack 并恢复对应数据源、参数和面包屑。
- 缺失来源字段不推进、不发空参数查询。
- 旧 `cardId + paramName` 两级下钻经归一化后保持原行为。
- API fixture 通过显式参数模板消费映射；SQL/Card/Dataset/Metric 继续使用各自既有适配入口，没有新增数据源专用下钻分支。
- 页面内组件和顶层组件执行相同 ScreenConfig 下钻校验。
