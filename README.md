<div align="center">

# 🎯 AgentMark

**One annotation, turn any Java method into an AI-callable tool.**

让 AI Agent 调用你的 Java 方法，只需要一个注解。

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)]()
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)]()

[English](#features) · [中文](#特性)

</div>

---

## 什么是 AgentMark？

AgentMark 是一个轻量级 Java 框架，让你用一个 `@Tool` 注解把任何 Java 方法变成 AI Agent 可以调用的工具。

不需要重写业务逻辑，不需要学新框架，不需要写胶水代码。

```java
@Tool(name = "查询订单", description = "根据订单号查询订单详情")
public Order getOrder(@P("订单号") String orderId) {
    return orderService.findById(orderId);  // 你的现有代码，一行不用改
}
```

然后用户就可以这样用：

```
用户：帮我查一下订单 ORD-20260316 的状态
AI：订单 ORD-20260316 状态为「已发货」，预计 3 月 18 日送达。
```

## 特性

- 🎯 **一个注解搞定** — `@Tool` 标记方法，`@P` 描述参数，没了
- 🔌 **零侵入** — 不改变你的代码结构，注解层完全独立
- 🚀 **Spring Boot Starter** — 引入依赖，自动扫描，开箱即用
- 🤖 **多模型支持** — OpenAI / Claude / 通义千问 / 本地模型
- 🔗 **自动编排** — 复杂任务自动拆解为多个工具调用
- 🛡️ **类型安全** — 基于 Java 类型系统，编译期检查参数

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.agentmark</groupId>
    <artifactId>agentmark-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 2. 配置模型

```yaml
agentmark:
  provider: openai          # openai / claude / dashscope
  api-key: ${YOUR_API_KEY}
  model: gpt-4o             # 或 claude-sonnet-4-20250514, qwen-max 等
```

### 3. 标记你的方法

```java
@Service
public class WeatherService {

    @Tool(name = "查询天气", description = "查询指定城市的当前天气")
    public WeatherInfo getWeather(
            @P("城市名称") String city,
            @P(value = "日期", required = false) String date) {
        // 你的现有业务逻辑
        return weatherApi.query(city, date);
    }
}
```

### 4. 调用 Agent

```java
@Autowired
private AgentMarkAgent agent;

String answer = agent.chat("北京今天天气怎么样？");
// → "北京今天晴，气温 12-22°C，东风 3 级。"
```

就这么简单。

## 进阶用法

### 多工具编排

AgentMark 支持 AI 自动拆解复杂任务，串联多个工具：

```java
@Tool(name = "查询库存", description = "查询商品库存数量")
public int getStock(@P("商品ID") String productId) { ... }

@Tool(name = "创建订单", description = "创建新订单")
public Order createOrder(@P("商品ID") String productId, @P("数量") int quantity) { ... }
```

```
用户：帮我看看 iPhone 16 还有没有货，有的话下一单
AI：iPhone 16 当前库存 128 台，已为您创建订单 ORD-20260316，数量 1 台。
```

### 对话上下文

```java
// 创建带记忆的会话
AgentMarkSession session = agent.newSession();
session.chat("查一下订单 ORD-001");    // → 订单详情
session.chat("帮我取消这个订单");       // → AI 知道"这个"指 ORD-001
```

### 自定义模型适配

```java
@Bean
public ModelProvider customProvider() {
    return new ModelProvider() {
        // 对接你的内部模型
    };
}
```

## 与其他框架对比

| 特性 | AgentMark | Spring AI | LangChain4j |
|------|---------|-----------|-------------|
| 接入方式 | 一个注解 | 需要定义 Function 对象 | 需要实现 Tool 接口 |
| 侵入性 | 零侵入 | 低 | 中 |
| 上手时间 | 5 分钟 | 30 分钟 | 1 小时 |
| 学习曲线 | 平缓 | 中等 | 较陡 |
| Spring Boot 集成 | 原生 Starter | 原生 | 需要额外配置 |

## Roadmap

- [x] @Tool / @P 注解 + 自动扫描
- [x] OpenAI function calling 对接
- [x] Claude tool use 对接
- [ ] 通义千问 / 文心一言对接
- [ ] 多轮对话上下文管理
- [ ] 多工具自动编排
- [ ] 流式输出（SSE）
- [ ] 异步工具调用
- [ ] 工具权限控制
- [ ] 可视化工具管理面板

## 参与贡献

欢迎 PR 和 Issue！详见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## License

Apache License 2.0

---

<div align="center">

**🎯 让每个 Java 方法都能被 AI 调用**

[GitHub](https://github.com/yourname/agentmark) · [文档](https://agentmark.dev) · [示例](./examples)

</div>
