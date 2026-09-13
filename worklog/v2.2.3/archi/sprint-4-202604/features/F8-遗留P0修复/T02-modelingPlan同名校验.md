# T02: modeling_plan 同名校验

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
创建/更新 modeling_plan 时检测同名，防止重复创建。

## 技术设计

### 改动点
1. `ModelingAuxResource` 或对应 Service 中，创建前查询：
```java
if (planRepository.existsByNameIgnoreCase(request.getName())) {
    throw new BadRequestException("已存在同名项目空间: " + request.getName());
}
```
2. 更新时排除自身：
```java
if (planRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), existingId)) {
    throw new BadRequestException("已存在同名项目空间: " + request.getName());
}
```

### 影响范围
| 文件 | 改动 |
|------|------|
| `ModelingAuxResource.java` 或对应 Service | 同名校验 |
| `ModelingPlanRepository.java` | 新增查询方法 |

## 验证
- [ ] 创建同名 plan 返回 400
- [ ] 更新不改名时不报错
- [ ] 更新改成已有名称时报错

## 完成标准
- [ ] 同名校验实现
