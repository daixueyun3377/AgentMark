# AgentMark

**One annotation, turn any Java method into an AI-callable tool.**

让 AI Agent 调用你的 Java 方法，只需要一个注解。

---

## 特性

- 🎯 **一个注解搞定** — `@AgentMark` 标记方法，`@ParamDesc` 可选描述参数
- 🧠 **AI 自动推断** — 不加 `@ParamDesc` 也能用，AI 根据参数名和类型自动理解
- 🔗 **复杂类型支持** — 嵌套对象、`List`、`Map`、枚举等自动生成 JSON Schema
- 🤖 **多模型支持** — Claude（P0）/ OpenAI / 通义千问 / 兼容接口
- 🚀 **Spring Boot Starter** — 引入依赖，自动扫描，开箱即用
- 🔌 **零侵入** — 不改变你的代码结构，注解层完全独立
- 🔗 **自动编排** — 复杂任务自动拆解为多个工具调用
- 🛡️ **类型安全** — 基于 Java 类型系统，编译期检查参数

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.daixueyun3377</groupId>
    <artifactId>agentmark-spring-boot-starter</artifactId>
    <version>0.0.3</version>
</dependency>
```

### 2. 配置 API

在 `application.yml` 中配置模型提供者：

**Claude（推荐，默认）：**
```yaml
agentmark:
  provider: claude
  api-key: ${CLAUDE_API_KEY}
  model: claude-sonnet-4-20250514
  # base-url: https://api.anthropic.com/  # 默认值，可不填
```

**OpenAI：**
```yaml
agentmark:
  provider: openai
  api-key: ${OPENAI_API_KEY}
  model: gpt-4o
  base-url: https://api.openai.com/v1/
```

**通义千问（DashScope）：**
```yaml
agentmark:
  provider: dashscope
  api-key: ${DASHSCOPE_API_KEY}
  model: qwen-max
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1/
```

### 3. 标记你的方法

```java
@Service
public class WeatherService {

    // 最简用法：只加 @AgentMark，AI 自动推断参数
    @AgentMark(name = "查询天气", description = "查询指定城市的当前天气")
    public WeatherInfo getWeather(String city) {
        return weatherApi.query(city);
    }

    // 需要补充说明时，用 @ParamDesc（可选）
    @AgentMark(name = "计算器", description = "四则运算")
    public double calculate(
            double a,
            @ParamDesc("运算符：+、-、*、/") String operator,
            double b) {
        // ...
    }
}
```

### 4. 调用 Agent

```java
@Autowired
private AgentMarkAgent agent;

String answer = agent.chat("北京今天天气怎么样？");
// → "北京今天晴，气温 22°C，东风 3 级。"
```

就这么简单。

## 核心注解

### @AgentMark

标记方法为 AI 可调用的工具。

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | String | 否 | 工具名称（中文），为空时用方法名 |
| `description` | String | 否 | 工具描述，帮助 AI 理解何时调用 |

```java
// 完整写法
@AgentMark(name = "查询订单", description = "根据订单号查询订单详情")
public Order getOrder(String orderId) { ... }

// 极简写法：name 和 description 都可以省略
@AgentMark
public Order getOrder(String orderId) { ... }
```

### @ParamDesc

可选注解，为参数添加额外描述。不加时 AI 根据参数名和类型自动推断。

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `value` | String | 是 | 参数描述 |
| `required` | boolean | 否 | 是否必填，默认 `true` |

可以标注在方法参数或 POJO 字段上：

```java
// 标注在方法参数上
public Order getOrder(@ParamDesc("订单号，格式如 ORD-20260316") String orderId) { ... }

// 标注在 POJO 字段上
public class OrderRequest {
    @ParamDesc("客户姓名")
    private String customerName;

    @ParamDesc(value = "备注信息", required = false)
    private String remark;
}
```

## 复杂嵌套类型

AgentMark 自动将 Java 类型转换为 JSON Schema，支持任意嵌套深度：

```java
@AgentMark(name = "创建订单", description = "根据用户需求创建订单")
public OrderResult createOrder(OrderRequest order) {
    // AI 会自动理解 OrderRequest 的完整结构
}

public class OrderRequest {
    private String customerName;
    private String shippingAddress;
    private List<OrderItem> items;  // 嵌套 List
}

public class OrderItem {
    private String productName;
    private int quantity;
    private double price;
}
```

用户只需说：
> "帮张三下个单，iPhone 16 一台 7999，AirPods Pro 两副每副 1899，寄到北京朝阳区"

AI 会自动构造完整的嵌套对象调用工具。

**支持的类型：**
- 基本类型：`String`, `int`, `long`, `double`, `float`, `boolean`, `BigDecimal`
- 日期类型：`Date`, `LocalDate`, `LocalDateTime`
- 集合类型：`List<T>`, `Set<T>`, `Map<K,V>`
- 数组：`T[]`
- 枚举：自动生成 `enum` 约束
- 嵌套对象：递归解析所有字段
- 循环引用：自动检测并防护

## 多轮对话

```java
AgentMarkSession session = agent.newSession();
session.chat("查一下订单 ORD-001");     // → 订单详情
session.chat("帮我取消这个订单");        // → AI 知道"这个"指 ORD-001
```

## REST API

示例项目提供了 HTTP 接口：

```bash
# 启动
mvn spring-boot:run -pl agentmark-example

# 调用
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "北京今天天气怎么样？"}'
```

## 项目结构

```
AgentMark/
├── agentmark-core/                  # 核心模块
│   ├── annotation/                  # @AgentMark, @ParamDesc 注解
│   ├── agent/                       # AgentMarkAgent, AgentMarkSession
│   ├── model/                       # ToolDefinition, ToolParameter, ToolResult
│   ├── provider/                    # ModelProvider 接口
│   │   ├── claude/ClaudeProvider    # Claude API 对接
│   │   └── openai/OpenAiProvider    # OpenAI API 对接
│   └── registry/ToolRegistry        # 工具注册中心 + JSON Schema 生成
├── agentmark-spring-boot-starter/   # Spring Boot 自动配置
└── agentmark-example/               # 示例项目
```

## 环境要求

- JDK 1.8+
- Spring Boot 2.7+
- Maven 3.6+

### ⚠️ 重要：编译参数

必须在 `pom.xml` 中添加 `-parameters` 编译参数，否则反射获取的参数名会变成 `arg0`、`arg1`：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <source>1.8</source>
        <target>1.8</target>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

使用 `agentmark-spring-boot-starter` 时，需要在你的项目中添加此配置。

## 自定义模型提供者

```java
@Bean
public ModelProvider customProvider() {
    return new ModelProvider() {
        @Override
        public ChatResponse chat(String userMessage, Collection<ToolDefinition> tools, List<ChatMessage> history) {
            // 对接你的内部模型
        }

        @Override
        public ChatResponse submitToolResults(List<ChatMessage> history, Collection<ToolDefinition> tools) {
            // 处理工具调用结果
        }
    };
}
```

## License

Apache License 2.0
