# AgentMark

[![Maven Central](https://img.shields.io/maven-central/v/io.github.daixueyun3377/agentmark-spring-boot-starter)](https://central.sonatype.com/artifact/io.github.daixueyun3377/agentmark-spring-boot-starter)
[![License](https://img.shields.io/github/license/daixueyun3377/AgentMark)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7%2B-green)](https://spring.io/projects/spring-boot)
[![Javadoc](https://img.shields.io/badge/Javadoc-online-blue)](https://daixueyun3377.github.io/AgentMark/)

**One annotation, turn any Java method into an AI-callable tool.**

让 AI Agent 调用你的 Java 方法，只需要一个注解。

---

## 特性

- 🎯 **一个注解搞定** — `@AgentMark` 标记方法，`@ParamDesc` 可选描述参数
- 🧠 **AI 自动推断** — 不加 `@ParamDesc` 也能用，AI 根据参数名和类型自动理解
- 🔗 **复杂类型支持** — 嵌套对象、`List`、`Map`、枚举等自动生成 JSON Schema
- 🤖 **多模型支持** — Claude（默认）/ OpenAI / 任何 OpenAI 兼容接口（通义千问、DeepSeek 等）
- 🚀 **Spring Boot Starter** — 引入依赖，自动扫描，开箱即用（所有单例 Bean 初始化完成后再扫描注册，不影响其他 Bean 的启动顺序）
- 🔌 **零侵入** — 不改变你的代码结构，注解层完全独立
- 🔗 **自动编排** — 复杂任务自动拆解为多个工具调用
- 🛡️ **类型安全** — 基于 Java 类型系统，编译期检查参数

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.daixueyun3377</groupId>
    <artifactId>agentmark-spring-boot-starter</artifactId>
    <version>0.0.5</version>
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

**通义千问（通过 OpenAI 兼容接口）：**
```yaml
agentmark:
  provider: openai
  api-key: ${DASHSCOPE_API_KEY}
  model: qwen-max
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1/
```

> **说明：** 通义千问、DeepSeek、Moonshot 等提供 OpenAI 兼容接口的模型，`provider` 统一填 `openai`，通过 `base-url` 指向对应的 API 地址即可。

### 3. 标记你的方法

```java
import io.github.daixueyun3377.agentmark.core.annotation.AgentMark;
import io.github.daixueyun3377.agentmark.core.annotation.ParamDesc;

@Service
public class WeatherService {

    @AgentMark(name = "getWeather", description = "查询指定城市的当前天气")
    public WeatherInfo getWeather(@ParamDesc("城市名称") String city) {
        return weatherApi.query(city);
    }

    @AgentMark(name = "calculate", description = "四则运算")
    public double calculate(
            @ParamDesc("第一个数") double a,
            @ParamDesc("运算符：+、-、*、/") String operator,
            @ParamDesc("第二个数") double b) {
        // ...
    }
}
```

### 4. 调用 Agent

```java
import io.github.daixueyun3377.agentmark.core.agent.AgentMarkAgent;

@Autowired
private AgentMarkAgent agent;

String answer = agent.chat("北京今天天气怎么样？");
// → "北京今天晴，气温 22°C，东风 3 级。"
```

就这么简单。

## 核心注解

### @AgentMark

标记方法为 AI 可调用的工具。

```java
import io.github.daixueyun3377.agentmark.core.annotation.AgentMark;
```

| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `name` | String | 否 | `""`（使用方法名） | 工具名称，**仅支持英文和下划线**，为空时用方法名 |
| `description` | String | 否 | `""`（空描述） | 工具描述，帮助 AI 理解何时调用 |

```java
// 推荐写法：提供 name 和 description，AI 理解更准确
@AgentMark(name = "getOrder", description = "根据订单号查询订单详情")
public Order getOrder(String orderId) { ... }

// 极简写法：name 默认用方法名，description 为空
// 适合方法名本身已经足够清晰的场景
@AgentMark
public Order getOrder(String orderId) { ... }
```

### @ParamDesc

可选注解，为参数添加额外描述。不加时 AI 根据参数名和类型自动推断。

```java
import io.github.daixueyun3377.agentmark.core.annotation.ParamDesc;
```

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
@AgentMark(name = "createOrder", description = "根据用户需求创建订单")
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
- 日期类型：`Date`, `LocalDate`, `LocalDateTime`, `LocalTime`, `Instant`, `OffsetDateTime`, `ZonedDateTime`
- 集合类型：`List<T>`, `Set<T>`, `Map<K,V>`
- 数组：`T[]`
- 枚举：自动生成 `enum` 约束
- 嵌套对象：递归解析所有字段
- 循环引用：自动检测并防护

## 多轮对话

```java
import io.github.daixueyun3377.agentmark.core.agent.AgentMarkAgent;
import io.github.daixueyun3377.agentmark.core.agent.AgentMarkSession;

AgentMarkSession session = agent.newSession();
session.chat("查一下订单 ORD-001");     // → 订单详情
session.chat("帮我取消这个订单");        // → AI 知道"这个"指 ORD-001
```

## REST API 集成示例

在你的 Spring Boot 项目中创建 Controller，即可通过 HTTP 与 Agent 对话：

```java
import io.github.daixueyun3377.agentmark.core.agent.AgentMarkAgent;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentMarkAgent agent;

    public AgentController(AgentMarkAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String reply = agent.chat(message);
        return Collections.singletonMap("reply", reply);
    }
}
```

启动你的 Spring Boot 应用后：

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "北京今天天气怎么样？"}'
```

## 多模块项目集成

如果你的项目是多模块结构（如 `app-api`、`app-service`、`app-common` 等），推荐如下方式引入：

**方式一：在业务模块中引入 starter（推荐）**

直接在使用 `@AgentMark` 注解的业务模块中引入 starter 依赖：

```xml
<!-- app-service/pom.xml -->
<dependency>
    <groupId>io.github.daixueyun3377</groupId>
    <artifactId>agentmark-spring-boot-starter</artifactId>
    <version>0.0.5</version>
</dependency>
```

确保 Spring Boot 启动类能扫描到标注了 `@AgentMark` 的 Bean（通常 `@SpringBootApplication` 的包路径覆盖即可）。

**方式二：拆分依赖**

如果只想在业务模块中使用注解，不想引入 Spring Boot 自动配置：

```xml
<!-- app-service/pom.xml — 只引入核心注解 -->
<dependency>
    <groupId>io.github.daixueyun3377</groupId>
    <artifactId>agentmark-core</artifactId>
    <version>0.0.5</version>
</dependency>

<!-- app-boot/pom.xml — 启动模块引入 starter -->
<dependency>
    <groupId>io.github.daixueyun3377</groupId>
    <artifactId>agentmark-spring-boot-starter</artifactId>
    <version>0.0.5</version>
</dependency>
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
│   │   └── openai/OpenAiProvider    # OpenAI 兼容 API 对接
│   └── registry/ToolRegistry        # 工具注册中心 + JSON Schema 生成
├── agentmark-spring-boot-starter/   # Spring Boot 自动配置
└── agentmark-example/               # 示例项目
```

## 环境要求

- JDK 1.8+
- Spring Boot 2.7+（兼容 Spring Boot 3.x，需 JDK 17+）
- Maven 3.6+

### 依赖说明

AgentMark 引入以下依赖，starter 会自动传递，无需手动添加：

| 依赖 | 版本 | 说明 |
|------|------|------|
| `jackson-databind` | 2.17.0 | JSON 序列化/反序列化，构建工具参数 Schema |
| `jackson-datatype-jsr310` | 2.17.0 | Java 8 日期类型（LocalDate、LocalDateTime 等）序列化支持 |
| `okhttp3` | 4.12.0 | HTTP 客户端，调用 LLM API |
| `slf4j-api` | 由 Spring Boot 管理 | 日志门面 |
| `spring-boot-starter` | 2.7.18 | Spring Boot 自动配置基础 |

> **版本冲突提示：** 如果你的项目已有上述依赖的不同版本，Spring Boot 的 `dependencyManagement` 通常会统一管理。若遇到版本冲突，可在你的 `pom.xml` 中通过 `<dependencyManagement>` 显式指定版本覆盖。

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

如果内置的 Claude / OpenAI Provider 不满足需求，可以自定义实现 `ModelProvider` 接口：

```java
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider;
import io.github.daixueyun3377.agentmark.core.model.ToolDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.List;

@Configuration
public class MyModelConfig {

    @Bean
    public ModelProvider customProvider() {
        return new ModelProvider() {
            @Override
            public ChatResponse chat(String userMessage,
                                     Collection<ToolDefinition> tools,
                                     List<ChatMessage> history) {
                // 对接你的内部模型
                // ChatMessage、ChatResponse、ToolCall 均为 ModelProvider 的内部类
                return new ChatResponse("回复内容", null);
            }

            @Override
            public ChatResponse submitToolResults(List<ChatMessage> history,
                                                  Collection<ToolDefinition> tools) {
                // 处理工具调用结果后继续对话
                return new ChatResponse("回复内容", null);
            }
        };
    }
}
```

> **注意：** `ChatMessage`、`ChatResponse`、`ToolCall` 均为 `ModelProvider` 的内部类，使用时通过 `ModelProvider.ChatMessage` 等方式引用，或在实现类内部直接使用。

## 完整 import 速查

```java
// 核心注解
import io.github.daixueyun3377.agentmark.core.annotation.AgentMark;
import io.github.daixueyun3377.agentmark.core.annotation.ParamDesc;

// Agent 与会话
import io.github.daixueyun3377.agentmark.core.agent.AgentMarkAgent;
import io.github.daixueyun3377.agentmark.core.agent.AgentMarkSession;

// 自定义 Provider 时需要
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider;
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider.ChatMessage;
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider.ChatResponse;
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider.ToolCall;
import io.github.daixueyun3377.agentmark.core.model.ToolDefinition;
```

## API 速查

### AgentMarkAgent

| 方法 | 说明 |
|------|------|
| `String chat(String userMessage)` | 单轮对话，无上下文记忆 |
| `AgentMarkSession newSession()` | 创建带上下文的会话，支持多轮对话 |

### AgentMarkSession

| 方法 | 说明 |
|------|------|
| `String chat(String userMessage)` | 发送消息并获取回复，自动保持上下文 |
| `void clear()` | 清除对话历史 |

### @AgentMark

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | `""` | 工具名称，仅支持英文和下划线，为空时用方法名 |
| `description` | String | `""` | 工具描述，帮助 AI 理解何时调用 |

### @ParamDesc

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | — | 参数描述（必填） |
| `required` | boolean | `true` | 是否必填 |

### ModelProvider（接口）

| 方法 | 说明 |
|------|------|
| `ChatResponse chat(String userMessage, Collection<ToolDefinition> tools, List<ChatMessage> history)` | 发送用户消息，返回模型回复 |
| `ChatResponse submitToolResults(List<ChatMessage> history, Collection<ToolDefinition> tools)` | 提交工具调用结果，继续对话 |

**内部类：**

| 类 | 说明 |
|------|------|
| `ModelProvider.ChatMessage` | 对话消息（user / assistant / tool） |
| `ModelProvider.ChatResponse` | 模型回复，包含文本和可能的工具调用 |
| `ModelProvider.ToolCall` | 工具调用请求，包含 id、name、arguments |

### 配置属性（application.yml）

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `agentmark.provider` | String | `claude` | 模型提供者：`claude` / `openai` |
| `agentmark.api-key` | String | — | API Key |
| `agentmark.model` | String | `claude-sonnet-4-20250514` | 模型名称 |
| `agentmark.base-url` | String | `https://api.anthropic.com/` | API 基础地址 |

> 📖 **在线 Javadoc：** [https://daixueyun3377.github.io/AgentMark/](https://daixueyun3377.github.io/AgentMark/)

## 社区 & 联系

- 📚 [Javadoc 在线文档](https://daixueyun3377.github.io/AgentMark/)
- 🐛 [Bug 报告](https://github.com/daixueyun3377/AgentMark/issues/new?template=bug_report.yml)
- 💡 [功能建议](https://github.com/daixueyun3377/AgentMark/issues/new?template=feature_request.yml)
- 💬 [Discussions](https://github.com/daixueyun3377/AgentMark/discussions)
- 📧 Email: [daixueyun3377@gmail.com](mailto:daixueyun3377@gmail.com)

## License

Apache License 2.0
