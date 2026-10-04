# T01: ExpressionParser（ANTLR grammar + visitor）

**优先级**: P0
**状态**: READY
**依赖**: F1/T03

## 目标

把 F1-T03 spec 里定义的 DSL 文法实现为 ANTLR4 grammar，并写一个 Visitor 产出**抽象语法树（AST）**，供 T02 编译到 SQL 使用。grammar 文件同时用于前端（TS）做 linting，避免"后端通过、前端校验不过"。

## 技术设计

### 1. 工程约定

- Grammar 文件：`DerivedMetric.g4`（已在 F1/T03 起草，本 Task 正式化）
- Maven 插件：`antlr4-maven-plugin` 生成 Java parser
- npm 侧：`antlr4ts` 或 `antlr4-typescript-runtime` 生成 TS parser（仅用于 lint）
- 统一 grammar 路径：`source/dts-platform/src/main/antlr4/com/yuzhi/dts/platform/service/semantic/expression/DerivedMetric.g4`

### 2. Grammar 大纲

```antlr
grammar DerivedMetric;

// Parser rules
expression : addExpression ;
addExpression : mulExpression (('+' | '-') mulExpression)* ;
mulExpression : unary (('*' | '/') unary)* ;
unary : '-' unary | primary ;

primary
    : literal
    | reference
    | functionCall
    | conditional
    | '(' expression ')'
    ;

reference : '[' IDENT ('.' IDENT)* ']' ;
functionCall : IDENT '(' (argument (',' argument)*)? ')' ;
argument : expression ;
conditional : 'if' '(' comparison ',' expression ',' expression ')' ;
comparison : expression CMP_OP expression ;

literal : NUMBER | STRING | 'true' | 'false' | 'null' ;

// Lexer rules
CMP_OP : '=' | '!=' | '>=' | '<=' | '>' | '<' | 'in' ;
IDENT : [a-zA-Z_][a-zA-Z0-9_]* ;
NUMBER : [0-9]+ ('.' [0-9]+)? ;
STRING : '"' (~'"')* '"' | '\'' (~'\'')* '\'' ;
WS : [ \t\r\n]+ -> skip ;
```

### 3. AST 节点类型

```java
public sealed interface ExprNode {
    record Literal(Object value, ValueType type) implements ExprNode {}
    record Reference(List<String> path) implements ExprNode {}   // ["ads_sales_daily", "revenue"]
    record BinaryOp(String op, ExprNode left, ExprNode right) implements ExprNode {}
    record UnaryMinus(ExprNode operand) implements ExprNode {}
    record FunctionCall(String name, List<ExprNode> args) implements ExprNode {}
    record If(ExprNode cond, ExprNode then, ExprNode els) implements ExprNode {}
    record Comparison(String op, ExprNode left, ExprNode right) implements ExprNode {}
}
```

### 4. Visitor 实现

```java
public class DerivedMetricBuilder extends DerivedMetricBaseVisitor<ExprNode> {
    @Override public ExprNode visitExpression(...) { ... }
    // ...
}

@Service
public class ExpressionParser {
    public ExprNode parse(String source) throws DslParseException {
        CharStream input = CharStreams.fromString(source);
        DerivedMetricLexer lexer = new DerivedMetricLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(DslErrorListener.INSTANCE);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DerivedMetricParser parser = new DerivedMetricParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(DslErrorListener.INSTANCE);

        ParseTree tree = parser.expression();
        return new DerivedMetricBuilder().visit(tree);
    }
}
```

### 5. 错误监听器

```java
public class DslErrorListener extends BaseErrorListener {
    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine,
                            String msg, RecognitionException e) {
        throw new DslParseException(
            "DSL_E001",
            format("语法错误: %s (line %d:%d)", msg, line, charPositionInLine),
            line, charPositionInLine
        );
    }
}
```

### 6. 语义校验（parse 之后）

`DerivedMetricSemanticAnalyzer` 接 AST，做：
- 所有 `Reference` 解析到 metric/dimension（依赖 F2 meta）
- 函数名在白名单（F4/T03）
- 每个 function call 参数数量正确
- 类型推导：`[revenue] + [region]` → 报错（number + string）
- 循环依赖检测：`derived.[a] = [derived.b]`，`derived.[b] = [derived.a]` → 拒绝
- 最大嵌套深度 3（`derived.[a]` 可以引用 `derived.[b]`，`derived.[b]` 可以引用 `derived.[c]`，到 `derived.[c]` 不能再引用别的 derived）

### 7. 错误码表（至少）

| 码 | 含义 |
|---|---|
| DSL_E001 | 语法错 |
| DSL_E010 | 函数不在白名单 |
| DSL_E011 | 函数参数数量错 |
| DSL_E012 | 引用的指标不存在 |
| DSL_E013 | 引用的维度不存在 |
| DSL_E020 | 类型不匹配 |
| DSL_E021 | 运算符两侧类型不兼容 |
| DSL_E030 | 循环依赖 |
| DSL_E031 | 嵌套深度超限 |
| DSL_E040 | 密级越限引用 |

每个错误携带：`code`、`message_zh`、`message_en`、`location { line, col, offset, length }`、`hint`。

### 8. 预加载与缓存

ExpressionParser 本身是纯函数，parse 结果可以按 `(source, metadata_version)` 缓存。不做过度工程化，简单 Caffeine。

### 9. 测试用例

grammar 测试：
- 10 个正例（含 F1/T03 的 5 个样例 + 5 个额外）
- 10 个反例（空表达式、不配对括号、非法 token、注释、分号、子查询尝试）

语义测试：
- 引用不存在 metric → DSL_E012
- 循环依赖 → DSL_E030
- 类型不匹配 → DSL_E020
- 白名单拒绝 → DSL_E010（见 T03）

## 影响范围

| 类型 | 文件 |
|---|---|
| 新建 | `main/antlr4/.../DerivedMetric.g4` |
| 新建 | `service/semantic/expression/ExpressionParser.java` |
| 新建 | `service/semantic/expression/DerivedMetricSemanticAnalyzer.java` |
| 新建 | `service/semantic/expression/DslErrorListener.java` |
| 新建 | AST record hierarchy |
| 新建 | pom.xml 里 `antlr4-maven-plugin` |
| 新建 | 前端镜像 grammar（`source/dts-platform-webapp/src/pages/bi/dsl/grammar/DerivedMetric.g4`，同一份文件；可通过 CI 做 diff 检查一致） |
| 测试 | ANTLR 测试（纯 grammar 层）、Semantic 测试 |

## 验证

- [ ] Maven build 能生成 Java parser
- [ ] F1/T03 的 5 个样例全部 parse 通过
- [ ] F1/T03 的 5 个反例全部报错且错误码正确
- [ ] 10 个语义错场景返回明确错误码
- [ ] grammar 文件前后端一致（CI 做 diff）
- [ ] AST 可序列化为 JSON 供调试

## 完成标准

- [ ] parser 独立可用（无需接其他模块）
- [ ] 单元测试覆盖率 ≥ 90%
- [ ] 文档 `assets/specs/03-derived-metric-dsl.md` 与 grammar 一致
