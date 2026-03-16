package io.agentmark.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentmark.core.agent.AgentMarkAgent;
import io.agentmark.core.model.ToolDefinition;
import io.agentmark.core.provider.ModelProvider;
import io.agentmark.core.registry.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 2：模拟完整 Agent 调用流程（Mock Provider，不需要真实 API Key）。
 * 验证：用户消息 → AI 选择工具 → 参数转换 → 执行 → 返回结果。
 */
public class AgentFlowTest {

    private ToolRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.register(new DemoToolService());
    }

    @Test
    @DisplayName("简单调用：模拟 AI 调用查询天气")
    void simpleToolCall_weather() {
        // 模拟 AI 返回 tool_use：调用"查询天气"，参数 city=北京
        ModelProvider mockProvider = createMockProvider(
                "getWeather",
                Collections.singletonMap("city", (Object) "北京"),
                "北京今天晴，气温 22°C，东风 3 级。"
        );

        AgentMarkAgent agent = new AgentMarkAgent(registry, mockProvider);
        String reply = agent.chat("北京今天天气怎么样？");

        System.out.println("=== 简单调用：查询天气 ===");
        System.out.println("用户: 北京今天天气怎么样？");
        System.out.println("AI: " + reply);
        System.out.println();

        assertEquals("北京今天晴，气温 22°C，东风 3 级。", reply);
    }

    @Test
    @DisplayName("简单调用：模拟 AI 调用计算器")
    void simpleToolCall_calculator() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("a", 12.5);
        args.put("operator", "+");
        args.put("b", 7.5);

        ModelProvider mockProvider = createMockProvider(
                "calculate", args, "12.5 + 7.5 = 20.0"
        );

        AgentMarkAgent agent = new AgentMarkAgent(registry, mockProvider);
        String reply = agent.chat("帮我算一下 12.5 加 7.5");

        System.out.println("=== 简单调用：计算器 ===");
        System.out.println("用户: 帮我算一下 12.5 加 7.5");
        System.out.println("AI: " + reply);
        System.out.println();

        assertEquals("12.5 + 7.5 = 20.0", reply);
    }

    @Test
    @DisplayName("复杂嵌套调用：模拟 AI 调用创建订单（含 List<OrderItem>）")
    void complexToolCall_createOrder() {
        // 模拟 AI 构造的复杂嵌套参数
        Map<String, Object> orderArgs = new LinkedHashMap<>();
        orderArgs.put("customerName", "张三");
        orderArgs.put("shippingAddress", "北京市朝阳区xxx路123号");

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item1 = new LinkedHashMap<>();
        item1.put("productName", "iPhone 16");
        item1.put("quantity", 1);
        item1.put("price", 7999.0);
        items.add(item1);

        Map<String, Object> item2 = new LinkedHashMap<>();
        item2.put("productName", "AirPods Pro");
        item2.put("quantity", 2);
        item2.put("price", 1899.0);
        items.add(item2);

        orderArgs.put("items", items);

        // 整个 order 对象作为一个参数
        Map<String, Object> toolArgs = Collections.singletonMap("order", (Object) orderArgs);

        ModelProvider mockProvider = createMockProvider(
                "createOrder", toolArgs,
                "已为张三创建订单，共 2 种商品，总金额 ¥11,797.00。"
        );

        AgentMarkAgent agent = new AgentMarkAgent(registry, mockProvider);
        String reply = agent.chat("帮张三下个单，iPhone 16 一台 7999，AirPods Pro 两副 1899 一副，寄到北京市朝阳区xxx路123号");

        System.out.println("=== 复杂嵌套调用：创建订单 ===");
        System.out.println("用户: 帮张三下个单，iPhone 16 一台 7999，AirPods Pro 两副 1899 一副");
        System.out.println("AI: " + reply);
        System.out.println();

        assertEquals("已为张三创建订单，共 2 种商品，总金额 ¥11,797.00。", reply);
    }

    /**
     * 创建 Mock Provider：第一次返回 tool_use，第二次返回最终文本。
     */
    private ModelProvider createMockProvider(String toolName, Map<String, Object> args, String finalReply) {
        return new ModelProvider() {
            private int callCount = 0;

            @Override
            public ChatResponse chat(String userMessage, Collection<ToolDefinition> tools, List<ChatMessage> history) {
                callCount++;
                // 第一次调用：返回 tool_use
                List<ToolCall> toolCalls = Collections.singletonList(
                        new ToolCall("call_" + callCount, toolName, args)
                );
                return new ChatResponse(null, toolCalls);
            }

            @Override
            public ChatResponse submitToolResults(List<ChatMessage> history, Collection<ToolDefinition> tools) {
                callCount++;
                // 工具执行完后，返回最终文本
                return new ChatResponse(finalReply, null);
            }
        };
    }
}
