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
- 📝 **Markdown System Prompt** — 从 `agentmark/system-prompt.md` 加载角色设定，多 Agent 场景支持多个 `xxx-prompt.md`

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.daixueyun3377</groupId>
    <artifactId>agentmark-spring-boot-starter</artifactId>
    <version>1.0.5-SNAPSHOT</version>
</dependency>
```

如果你本机有引用okhttp3，且版本与此工程不匹配，请在```<dependencyManagement>```下单独引用
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
            <version>4.12.0</version>
        </dependency>
    </dependencies>
</dependencyManagement>
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
  # system-prompt-path: agentmark/system-prompt.md  # 可选，默认从该文件读取 system prompt
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

### 3. 编写 System Prompt

在 `src/main/resources/agentmark/system-prompt.md` 中定义 LLM 的角色与行为（启动时自动加载）：

```markdown
你是一个专业助手，回答要简洁准确。
根据用户问题调用合适的工具完成任务。
不确定的信息不要编造。
```

文件不存在时默认 Agent 不注入 system prompt，不影响工具调用。自定义路径：

```yaml
agentmark:
  system-prompt-path: agentmark/system-prompt.md  # classpath 相对路径，默认值
```

### 4. 标记你的方法

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

### 5. 调用 Agent

```java
import io.github.daixueyun3377.agentmark.core.agent.AgentMarkAgent;
import io.github.daixueyun3377.agentmark.core.model.ChatResult;

@Autowired
private AgentMarkAgent agent;

ChatResult result = agent.chat("北京今天天气怎么样？");
System.out.println(result.getText());       // "北京今天晴，气温 22°C，东风 3 级。"
System.out.println(result.getTraceId());    // trace 开启时返回追踪 ID
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

## 系统提示词（System Prompt）

> **v1.0.5 变更：** 不再支持在 `application.yml` 中通过 `agentmark.system-prompt` 内联配置，改为从 Markdown 文件加载。请迁移到 `agentmark/system-prompt.md`。

在应用工程的 `src/main/resources/agentmark/system-prompt.md` 中编写 system prompt，启动时由 `SystemPromptLoader` 自动加载：

```markdown
你是一个招聘助手，回答要简洁专业。
只回答与岗位、招聘相关的问题。
不确定的信息不要编造。
```

如需自定义文件路径，可在 `application.yml` 中配置：

```yaml
agentmark:
  system-prompt-path: agentmark/system-prompt.md  # 默认值，可改为其他 classpath 路径
```

### 多业务场景（多 Agent）

不同业务场景在 `agentmark/` 目录下放置多个 `xxx-prompt.md`，在代码中通过 `SystemPromptLoader` 加载并创建对应 Agent：

```
src/main/resources/agentmark/
├── system-prompt.md      # 默认 Agent（自动配置）
├── recruit-prompt.md     # 招聘 Agent
└── customer-prompt.md    # 客服 Agent
```

```java
import io.github.daixueyun3377.agentmark.spring.SystemPromptLoader;
import io.github.daixueyun3377.agentmark.spring.AgentMarkProperties;

@Configuration
public class AgentConfig {

    @Bean("recruitAgent")
    public AgentMarkAgent recruitAgent(ToolRegistry registry, ModelProvider provider,
                                       SystemPromptLoader promptLoader, AgentMarkProperties props) {
        String prompt = promptLoader.load("agentmark/recruit-prompt.md");
        return new AgentMarkAgent(registry, provider, null, prompt, props.getMaxToolRounds());
    }

    @Bean("customerAgent")
    public AgentMarkAgent customerAgent(ToolRegistry registry, ModelProvider provider,
                                        SystemPromptLoader promptLoader, AgentMarkProperties props) {
        String prompt = promptLoader.load("agentmark/customer-prompt.md");
        return new AgentMarkAgent(registry, provider, null, prompt, props.getMaxToolRounds());
    }
}
```

使用时通过 `@Qualifier` 指定：

```java
@Autowired
@Qualifier("recruitAgent")
private AgentMarkAgent agent;
```

> **注意：** 一旦注册了自定义 `AgentMarkAgent` Bean，自动配置的默认 Bean 将不再创建。默认 Agent 使用 `system-prompt-path` 指定的文件；多 Agent 场景由业务代码指定各自的 prompt 文件路径。

## 调用追踪（Trace）

开启后，每次对话的完整调用链会自动存储为 JSON 文件：

```yaml
agentmark:
  trace:
    enabled: true                    # 监控开关（默认关闭）
    path: /var/log/agentmark/traces  # 存储路径
```

`chat()` 返回的 `traceId` 可用于关联对应的 trace 文件：

```java
ChatResult result = agent.chat("查询上海岗位");
result.getText();     // LLM 回复
result.getTraceId();  // "a3f8c1e2"（trace 关闭时为 null）
```

生成的 trace 文件（如 `a3f8c1e2_20250519132200.json`）：

```json
{
  "traceId": "a3f8c1e2",
  "requestTime": "2025-05-19T13:22:00",
  "userMessage": "查询上海岗位",
  "reply": "...",
  "llmCallCount": 4,
  "toolCallCount": 4,
  "totalDurationMs": 52142,
  "callChain": [
    {"type": "llm", "durationMs": 3200, "input": "查询上海岗位", "output": {"toolCalls": [{"tool": "queryJobs", "arguments": {"city": "上海"}}]}},
    {"type": "tool", "name": "queryJobs", "durationMs": 8500, "input": {"city": "上海"}, "output": [...]},
    {"type": "llm", "durationMs": 4100, "input": [{"tool": "queryJobs", "result": [...]}], "output": {"text": "..."}}
  ]
}
```

关闭 trace 时零开销，不收集不写文件。

## 多轮对话

```java
import io.github.daixueyun3377.agentmark.core.agent.AgentMarkAgent;
import io.github.daixueyun3377.agentmark.core.agent.AgentMarkSession;
import io.github.daixueyun3377.agentmark.core.agent.AgentMarkSessionManager;

AgentMarkSession session = agent.newSession();
session.chat("查一下订单 ORD-001").getText();     // → 订单详情
session.chat("帮我取消这个订单").getText();        // → AI 知道"这个"指 ORD-001

AgentMarkSessionManager sessionManager = new AgentMarkSessionManager(agent);
String sessionId = sessionManager.createSession();
sessionManager.chat(sessionId, "查一下订单 ORD-001");
sessionManager.chat(sessionId, "帮我取消这个订单"); // → 通过同一个 sessionId 复用上下文
```

Spring Boot Starter 默认会创建 `AgentMarkSessionManager` Bean。`traceId` 是单次请求的追踪 ID，`sessionId` 是多轮对话的上下文 ID，二者用途不同。

## REST API 集成示例

在你的 Spring Boot 项目中创建 Controller，即可通过 HTTP 与 Agent 对话：

```java
import io.github.daixueyun3377.agentmark.core.agent.AgentMarkSessionManager;
import io.github.daixueyun3377.agentmark.core.model.ChatResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentMarkSessionManager sessionManager;

    public AgentController(AgentMarkSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = sessionManager.createSession();
        }

        ChatResult result = sessionManager.chat(sessionId, request.getMessage());
        return new ChatResponse(sessionId, result.getText(), result.getTraceId());
    }

    public static class ChatRequest {
        private String sessionId;
        private String message;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class ChatResponse {
        private final String sessionId;
        private final String reply;
        private final String traceId;

        public ChatResponse(String sessionId, String reply, String traceId) {
            this.sessionId = sessionId;
            this.reply = reply;
            this.traceId = traceId;
        }

        public String getSessionId() { return sessionId; }
        public String getReply() { return reply; }
        public String getTraceId() { return traceId; }
    }
}
```

启动你的 Spring Boot 应用后：

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "北京今天天气怎么样？"}'
```

第二次请求带上返回的 `sessionId`，即可保持同一轮上下文：

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "上一次返回的 sessionId", "message": "那明天呢？"}'
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
    <version>1.0.5-SNAPSHOT</version>
</dependency>
```

确保 Spring Boot 启动类能扫描到标注了 `@AgentMark` 的 Bean（通常 `@SpringBootApplication` 的包路径覆盖即可）。`system-prompt.md` 放在启动模块的 `src/main/resources/agentmark/` 下。

**方式二：拆分依赖**

如果只想在业务模块中使用注解，不想引入 Spring Boot 自动配置：

```xml
<!-- app-service/pom.xml — 只引入核心注解 -->
<dependency>
    <groupId>io.github.daixueyun3377</groupId>
    <artifactId>agentmark-core</artifactId>
    <version>1.0.5-SNAPSHOT</version>
</dependency>

<!-- app-boot/pom.xml — 启动模块引入 starter，并放置 agentmark/system-prompt.md -->
<dependency>
    <groupId>io.github.daixueyun3377</groupId>
    <artifactId>agentmark-spring-boot-starter</artifactId>
    <version>1.0.5-SNAPSHOT</version>
</dependency>
```

## 多工具组合查询

AgentMark 支持 LLM 自动组合多个工具完成复杂任务，无需额外配置：

**并行调用** — LLM 一次返回多个工具调用，全部执行：

```
用户："北京天气怎么样，顺便算一下 100*3"
→ LLM 同时调用 getWeather(city="北京") + calculate(a=100, operator="*", b=3)
→ 合并结果后回复
```

**链式调用** — 拿到结果后 LLM 可以继续调用其他工具：

```
用户："上海有什么餐饮岗位，薪资多少？"
→ 第 1 轮：调用 queryJobs(city="上海", category="餐饮") → 拿到岗位列表
→ 第 2 轮：调用 getJobSalary(jobId="xxx") → 拿到薪资信息
→ 最终回复
```

最大调用轮数通过 `agentmark.max-tool-rounds` 配置（默认 10）。

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
import io.github.daixueyun3377.agentmark.core.agent.AgentMarkSessionManager;

// 自定义 Provider 时需要
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider;
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider.ChatMessage;
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider.ChatResponse;
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider.ToolCall;
import io.github.daixueyun3377.agentmark.core.model.ToolDefinition;

// Spring Boot 集成（多 Agent / 自定义 prompt 文件）
import io.github.daixueyun3377.agentmark.spring.SystemPromptLoader;
import io.github.daixueyun3377.agentmark.spring.AgentMarkProperties;
```

## API 速查

### AgentMarkAgent

| 方法 | 说明 |
|------|------|
| `ChatResult chat(String userMessage)` | 单轮对话，返回回复文本和 traceId |
| `AgentMarkSession newSession()` | 创建带上下文的会话，支持多轮对话 |

### AgentMarkSession

| 方法 | 说明 |
|------|------|
| `ChatResult chat(String userMessage)` | 发送消息并获取回复，自动保持上下文 |
| `void clear()` | 清除对话历史 |

### AgentMarkSessionManager

| 方法 | 说明 |
|------|------|
| `String createSession()` | 创建内存会话并返回 sessionId |
| `ChatResult chat(String sessionId, String userMessage)` | 使用指定 sessionId 发送消息，不存在或过期时创建新会话 |
| `boolean clear(String sessionId)` | 删除指定会话 |
| `void clearAll()` | 删除所有会话 |
| `void cleanupExpiredSessions()` | 清理过期会话 |
| `int size()` | 返回当前内存会话数 |

### ChatResult

| 方法 | 说明 |
|------|------|
| `String getText()` | 获取 LLM 最终回复文本 |
| `String getTraceId()` | 获取追踪 ID（trace 关闭时为 null） |

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

### SystemPromptLoader

| 方法 | 说明 |
|------|------|
| `String loadDefault()` | 加载默认路径 `agentmark/system-prompt.md` |
| `String load(String path)` | 加载指定 classpath 路径的 prompt 文件，不存在时返回 `null` |

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
| `agentmark.system-prompt-path` | String | `agentmark/system-prompt.md` | 默认 Agent 的 system prompt 文件（classpath 路径） |
| `agentmark.max-tool-rounds` | int | `10` | 单次对话最大工具调用轮数 |
| `agentmark.session.enabled` | boolean | `true` | 是否自动创建 `AgentMarkSessionManager` Bean |
| `agentmark.session.ttl-millis` | long | `1800000` | 会话空闲过期时间（毫秒），小于等于 0 表示不过期 |
| `agentmark.session.max-sessions` | int | `1000` | 最大内存会话数 |
| `agentmark.trace.enabled` | boolean | `false` | 调用追踪开关 |
| `agentmark.trace.path` | String | — | 追踪文件存储路径 |

> 📖 **在线 Javadoc：** [https://daixueyun3377.github.io/AgentMark/](https://daixueyun3377.github.io/AgentMark/)

## 社区 & 联系

- 📚 [Javadoc 在线文档](https://daixueyun3377.github.io/AgentMark/)
- 🐛 [Bug 报告](https://github.com/daixueyun3377/AgentMark/issues/new?template=bug_report.yml)
- 💡 [功能建议](https://github.com/daixueyun3377/AgentMark/issues/new?template=feature_request.yml)
- 💬 [Discussions](https://github.com/daixueyun3377/AgentMark/discussions)
- 📧 Email: [daixueyun3377@gmail.com](mailto:daixueyun3377@gmail.com)

## License

Apache License 2.0
