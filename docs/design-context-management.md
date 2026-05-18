# AgentMark 上下文管理设计方案

> 版本：v1.1-draft | 日期：2026-04-22 | 作者：飞天小猪

## 一、现状问题

`AgentMarkSession.history` 是无限增长的 `ArrayList<ChatMessage>`，每次请求全量发送给 API。

| 问题 | 影响 |
|------|------|
| 无 token 上限控制 | 对话一长直接超出 context window，API 报错 |
| token 消耗无限增长 | 费用失控，尤其 tool_call 带大量 JSON |
| 工具描述全量发送 | 工具多时 tools 定义本身就吃几千 token |

## 二、方案概览

两层递进优化：

```
第一层：精简工具描述（静态优化，零运行时开销）
第二层：对话历史摘要压缩（动态优化，滑动窗口 + LLM 摘要）
```

## 三、新增文件结构

```
agentmark-core/src/main/java/.../core/
  ├── context/                          ← 新增包
  │   ├── ContextConfig.java            — 配置项
  │   ├── ContextManager.java           — 编排器
  │   ├── ContextStrategy.java          — 策略接口
  │   ├── TokenEstimator.java           — Token 估算
  │   ├── NoLimitStrategy.java          — 不限制（向后兼容）
  │   ├── SlidingWindowStrategy.java    — 滑动窗口截断
  │   ├── SummaryStrategy.java          — 摘要 + 窗口（推荐默认）
  │   └── package-info.java
  ├── agent/
  │   ├── AgentMarkAgent.java           ← 改动
  │   └── AgentMarkSession.java         ← 小改
  ├── annotation/
  │   └── AgentMark.java                ← 新增 compact 属性
  ├── model/
  │   └── ToolDefinition.java           ← 新增 compact 字段
  └── provider/
      ├── claude/ClaudeProvider.java     ← 改动 buildSchemaNode
      └── openai/OpenAiProvider.java     ← 改动 buildSchemaNode

agentmark-spring-boot-starter/src/main/java/.../spring/
  ├── AgentMarkProperties.java          ← 新增 context / tools 配置
  └── AgentMarkAutoConfiguration.java   ← 注入 ContextManager
```

## 四、第一层：精简工具描述

### 4.1 目标

减少 tools 定义占用的 token。当工具数量 10+ 时，全量 schema 可能占 2000-5000 token。

### 4.2 改动点

#### 4.2.1 `@AgentMark` 注解新增 `compact`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentMark {
    String name() default "";
    String description() default "";
    boolean compact() default false;  // ← 新增
}
```

- `compact = true`：生成 schema 时省略所有参数的 `description` 字段
- `compact = false`（默认）：保持现有行为

#### 4.2.2 `ToolDefinition` 新增字段

```java
public class ToolDefinition {
    // ... 现有字段
    private boolean compact;

    public boolean isCompact() { return compact; }
    public void setCompact(boolean compact) { this.compact = compact; }
}
```

#### 4.2.3 `ToolRegistry` 扫描时读取

扫描 `@AgentMark` 注解构建 `ToolDefinition` 时，读取 `compact()` 值设置到 `ToolDefinition.compact`。

#### 4.2.4 Provider 的 `writeParameterSchema` 方法

两个 Provider（Claude / OpenAI）的 schema 构建方法增加 compact 参数：

```java
private void writeParameterSchema(ObjectNode prop, ToolParameter p, boolean compact) {
    prop.put("type", p.getType());
    // compact 模式下跳过 description
    if (!compact && p.getDescription() != null && !p.getDescription().isEmpty()) {
        prop.put("description", p.getDescription());
    }
    // enum、object、array 处理不变
}
```

调用链路：`doRequest` → `buildSchemaNode` → `writeParameterSchema`，把 `ToolDefinition.isCompact()` 透传下去。

#### 4.2.5 全局配置覆盖

```yaml
agentmark:
  tools:
    compact-schema: false  # true = 所有工具强制 compact，覆盖注解级别
```

优先级：`全局 compact-schema: true` > 注解 `compact = false`。

### 4.3 预期收益

| 工具数 | 优化前（估算） | 优化后 | 节省 |
|--------|---------------|--------|------|
| 5 个   | ~800 tokens   | ~500   | ~37% |
| 15 个  | ~3000 tokens  | ~1500  | ~50% |
| 30 个  | ~6000 tokens  | ~3000  | ~50% |

---

## 五、第二层：对话历史摘要压缩

### 5.1 目标

对话历史超过 token 阈值时，自动压缩早期对话为摘要，保留最近 N 轮完整对话。

### 5.2 核心设计原则

1. **存完整、发裁剪**：Session 保存完整历史，裁剪只发生在发送给模型时
2. **策略可插拔**：通过接口 + 配置切换，用户也可自定义策略
3. **增量摘要**：只对新增的旧消息生成摘要，不重复摘要已处理的部分
4. **tool_call 完整性**：裁剪时保证 tool_call / tool_result 配对不被切断
5. **向后兼容**：默认 `strategy: none`，行为与现有版本一致

### 5.3 类设计

#### 5.3.1 `ContextConfig` — 配置项

```java
public class ContextConfig {
    private String strategy = "none";        // none | sliding_window | summary
    private int maxContextTokens = 100000;   // 上下文 token 上限
    private int reservedTokens = 4096;       // 留给模型回复的预算
    private int summaryThreshold = 4096;     // 超过此值触发摘要
    private int recentKeepRounds = 5;        // 保留最近几轮完整对话
    private double charsPerToken = 2.5;      // token 估算系数
    private String summaryModel = null;      // null = 用主模型
    // getters & setters
}
```

#### 5.3.2 `TokenEstimator` — Token 估算

不引入 tiktoken 依赖，基于字符比例估算（精度 ±10%）：

```java
public class TokenEstimator {
    private final double charsPerToken; // 默认 2.5

    public int estimate(String text);           // 文本 → token 数
    public int estimate(ChatMessage msg);       // 单条消息 → token 数（含 role 开销 + tool_call JSON）
    public int estimateAll(List<ChatMessage>);  // 消息列表 → 总 token 数
}
```

估算逻辑：
- 每条消息固定开销 4 token（role + formatting）
- 文本：`Math.ceil(text.length() / charsPerToken)`
- tool_call：name + 10（结构开销）+ arguments JSON 长度估算

#### 5.3.3 `ContextStrategy` — 策略接口

```java
public interface ContextStrategy {
    /**
     * @param history    完整对话历史（只读）
     * @param config     配置
     * @param estimator  token 估算器
     * @param provider   模型提供者（摘要策略需要调 LLM）
     * @return 处理后的消息列表，用于发送给模型
     */
    List<ChatMessage> process(
        List<ChatMessage> history,
        ContextConfig config,
        TokenEstimator estimator,
        ModelProvider provider
    );
}
```

#### 5.3.4 `NoLimitStrategy` — 不限制

```java
// 直接返回 history 的副本，等同于现有行为
public List<ChatMessage> process(...) {
    return new ArrayList<>(history);
}
```

#### 5.3.5 `SlidingWindowStrategy` — 滑动窗口

算法：
1. 计算 token 预算 = `maxContextTokens - reservedTokens`
2. 从 history 末尾往前遍历，累加 token 直到超预算
3. 截取保留的部分
4. 修复 tool_call 完整性（见 5.4）

```
[被丢弃的早期消息] | [保留的最近消息 → 发送给模型]
                    ↑ 截断点
```

#### 5.3.6 `SummaryStrategy` — 摘要 + 窗口（核心）

算法：

```
完整历史: [msg1, msg2, ..., msg_old, ..., msg_recent1, ..., msg_N]
                              ↑                ↑
                        summarizedUpTo    recentStart

发送给模型: [摘要消息] + [msg_recent1, ..., msg_N]
```

详细流程：
1. 从后往前数 `recentKeepRounds` 个 user 消息，确定 `recentStart` 位置
2. 分割：`[summarizedUpTo, recentStart)` = 需要摘要的旧消息，`[recentStart, end)` = 保留原文
3. 如果旧消息的 token 数 > `summaryThreshold`，调用 LLM 生成摘要
4. 增量合并：新摘要与已有摘要合并（不是重新摘要全部）
5. 组装：`[摘要伪消息] + [近期原文]`
6. 安全检查：如果仍超预算，回退到滑动窗口裁剪近期消息

状态管理：
- `currentSummary: String` — 当前累积的摘要文本
- `summarizedUpTo: int` — 已摘要到的 history 索引
- `reset()` — Session.clear() 时调用

摘要 Prompt：

```
请将以下对话压缩为结构化摘要，严格遵循以下规则：

【必须保留】
1. 用户的核心需求和意图
2. 关键决策点和最终结论
3. 工具调用的结果要点（数据、计算结果、查询结果）
4. 未完成的任务或待确认事项
5. 用户明确表达的偏好或约束

【应该丢弃】
- 寒暄、礼貌用语
- 重复确认和中间推理过程
- 工具调用的原始 JSON（只保留结果要点）
- 已被后续决策覆盖的早期方案

输出格式：简洁的要点列表，不超过 500 字。
```

摘要注入方式：

```java
// 作为对话开头的一组伪消息
ChatMessage.user("[以下是早期对话的摘要]\n" + summary + "\n[摘要结束，以下是最近的对话]");
ChatMessage.assistant("好的，我已了解之前的对话背景，请继续。");
```

#### 5.3.7 `ContextManager` — 编排器

```java
public class ContextManager {
    private final ContextConfig config;
    private final ContextStrategy strategy;
    private final TokenEstimator estimator;

    public ContextManager(ContextConfig config);

    // 核心方法：处理历史，返回裁剪后的消息列表
    public List<ChatMessage> prepare(List<ChatMessage> history, ModelProvider provider);

    // 辅助方法
    public ContextStrategy getStrategy();
    public int estimateTokens(List<ChatMessage> messages);
}
```

构造时根据 `config.strategy` 自动创建对应策略实例。

### 5.4 Tool Call 完整性保证

滑动窗口裁剪可能切断 tool_call / tool_result 配对，导致 API 报错。

处理规则：
1. 如果裁剪后第一条是 `tool` role → 移除，直到遇到非 tool 消息
2. 如果第一条是 `assistant` 且有 `toolCalls`，但后续 `tool_result` 数量不够 → 整组移除
3. Claude 特殊处理：`tool_result` 的 role 是 `user`（带 `tool_use_id`），需要按 id 匹配

### 5.5 集成改动

#### AgentMarkAgent

```java
public class AgentMarkAgent {
    private final ContextManager contextManager;  // ← 新增

    // 新构造函数
    public AgentMarkAgent(ToolRegistry registry, ModelProvider provider, ContextManager contextManager);

    // 向后兼容旧构造函数
    public AgentMarkAgent(ToolRegistry registry, ModelProvider provider) {
        this(registry, provider, new ContextManager(new ContextConfig())); // 默认 none 策略
    }

    String processMessage(String userMessage, List<ChatMessage> history) {
        // ★ 发送前裁剪
        List<ChatMessage> contextMessages = contextManager.prepare(history, provider);
        ChatResponse response = provider.chat(userMessage, registry.getAllTools(), contextMessages);
        history.add(ChatMessage.user(userMessage));  // 完整历史照存

        // tool loop 中同样用裁剪后的上下文
        while (response.hasToolCalls() && rounds < MAX_TOOL_ROUNDS) {
            // ... 执行工具，结果加入 history ...
            List<ChatMessage> loopContext = contextManager.prepare(history, provider);
            response = provider.submitToolResults(loopContext, registry.getAllTools());
        }
        // ...
    }
}
```

#### AgentMarkSession

```java
public class AgentMarkSession {
    public void clear() {
        history.clear();
        // 重置摘要状态
        ContextStrategy strategy = agent.getContextManager().getStrategy();
        if (strategy instanceof SummaryStrategy) {
            ((SummaryStrategy) strategy).reset();
        }
    }

    // 新增：查询当前 token 用量
    public int estimateTokens() {
        return agent.getContextManager().estimateTokens(history);
    }

    // 新增：获取只读历史
    public List<ChatMessage> getHistory() {
        return Collections.unmodifiableList(history);
    }
}
```

#### AgentMarkAutoConfiguration

```java
@Bean
public ContextManager contextManager(AgentMarkProperties properties) {
    AgentMarkProperties.ContextProperties ctx = properties.getContext();
    ContextConfig config = new ContextConfig();
    config.setStrategy(ctx.getStrategy());
    config.setMaxContextTokens(ctx.getMaxContextTokens());
    config.setReservedTokens(ctx.getReservedTokens());
    config.setSummaryThreshold(ctx.getSummaryThreshold());
    config.setRecentKeepRounds(ctx.getRecentKeepRounds());
    config.setCharsPerToken(ctx.getCharsPerToken());
    return new ContextManager(config);
}

@Bean
public AgentMarkAgent agentMarkAgent(ToolRegistry registry, ModelProvider provider, ContextManager contextManager) {
    return new AgentMarkAgent(registry, provider, contextManager);
}
```

#### AgentMarkProperties

```java
@ConfigurationProperties(prefix = "agentmark")
public class AgentMarkProperties {
    // ... 现有字段

    private ContextProperties context = new ContextProperties();
    private ToolsProperties tools = new ToolsProperties();

    public static class ContextProperties {
        private String strategy = "none";
        private int maxContextTokens = 100000;
        private int reservedTokens = 4096;
        private int summaryThreshold = 4096;
        private int recentKeepRounds = 5;
        private double charsPerToken = 2.5;
        private String summaryModel = null;
        // getters & setters
    }

    public static class ToolsProperties {
        private boolean compactSchema = false;
        // getter & setter
    }
}
```

---

## 六、配置示例

```yaml
agentmark:
  provider: claude
  api-key: ${CLAUDE_API_KEY}
  model: claude-sonnet-4-20250514

  # 第一层：工具描述精简
  tools:
    compact-schema: false

  # 第二层：上下文管理
  context:
    strategy: summary            # none | sliding_window | summary
    max-context-tokens: 100000
    reserved-tokens: 4096
    summary-threshold: 4096
    recent-keep-rounds: 5
    chars-per-token: 2.5
```

---

## 七、向后兼容性

| 场景 | 行为 |
|------|------|
| 不配置 context | `strategy: none`，全量发送，与 v1.0 一致 |
| 不配置 tools | `compact-schema: false`，schema 不变 |
| 旧构造函数 `new AgentMarkAgent(registry, provider)` | 自动用 NoLimitStrategy |
| Session API | `chat()` / `clear()` 接口不变，新增 `estimateTokens()` / `getHistory()` |

---

## 八、风险与注意事项

| 风险 | 应对 |
|------|------|
| 摘要丢失关键信息 | Prompt 明确要求保留决策点和工具结果；保留最近 5 轮原文兜底 |
| 摘要 API 调用增加延迟和成本 | 增量摘要减少调用频次；后续可配置用更便宜的模型做摘要 |
| Token 估算不准 | ±10% 误差可接受；预留 reservedTokens 作为缓冲 |
| tool_call 配对被切断 | ensureToolCallIntegrity 修复逻辑 |
| 摘要失败 | catch 异常，保留已有摘要，不影响主流程 |

---

## 九、后续扩展（v1.2+）

- [ ] 摘要模型独立配置（用便宜模型做摘要）
- [ ] System Prompt 支持（ChatMessage 增加 system role）
- [ ] 工具语义路由（ToolRouter 接口，先留接口不实现）
- [ ] 精确 token 计数（可选引入 jtokkit 库）
- [ ] 摘要质量评估（对比摘要前后模型回答一致性）
