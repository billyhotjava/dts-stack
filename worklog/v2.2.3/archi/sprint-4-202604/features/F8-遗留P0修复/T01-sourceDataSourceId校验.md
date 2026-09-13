# T01: sourceDataSourceId 非空校验

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
IngestionTask 创建/更新时校验 sourceDataSourceId 不为空，防止 not-null constraint 异常。

## 技术设计

### 改动点
1. `IngestionTaskService` 创建/更新方法中增加校验：
```java
if (request.getSourceDataSourceId() == null) {
    throw new BadRequestException("来源数据源不能为空，请先选择数据源");
}
```
2. `IngestionTaskResource` 的 API 参数标记 `required = true`

### 影响范围
| 文件 | 改动 |
|------|------|
| `IngestionTaskService.java` | 添加校验 |
| `IngestionTaskResource.java` | 参数标记 |

## 验证
- [ ] sourceDataSourceId 为空时返回 400 + 可读错误
- [ ] 正常请求不受影响

## 完成标准
- [ ] 校验实现
