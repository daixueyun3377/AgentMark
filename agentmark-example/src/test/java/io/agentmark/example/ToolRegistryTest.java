package io.agentmark.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentmark.core.model.ToolDefinition;
import io.agentmark.core.model.ToolParameter;
import io.agentmark.core.registry.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 1：验证注解扫描 + JSON Schema 生成。
 * 覆盖简单参数和复杂嵌套类型。
 */
public class ToolRegistryTest {

    private ToolRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.register(new DemoToolService());
    }

    @Test
    @DisplayName("应该扫描到所有 @AgentMark 标记的方法")
    void shouldRegisterAllTools() {
        assertEquals(4, registry.getAllTools().size());
        assertNotNull(registry.getTool("getWeather"));
        assertNotNull(registry.getTool("getCurrentTime"));
        assertNotNull(registry.getTool("calculate"));
        assertNotNull(registry.getTool("createOrder"));
    }

    @Test
    @DisplayName("简单参数：查询天气 — 只有一个 String 参数，无 @ParamDesc")
    void simpleParam_weather() {
        ToolDefinition tool = registry.getTool("getWeather");
        assertTrue(tool.getDescription().contains("查询指定城市的当前天气信息"));
        assertEquals(1, tool.getParameters().size());

        ToolParameter p = tool.getParameters().get(0);
        assertEquals("city", p.getName());
        assertEquals("string", p.getType());
        assertTrue(p.isRequired());
        assertFalse(p.isComplex());

        System.out.println("=== 查询天气 ===");
        System.out.println("参数名: " + p.getName() + ", 类型: " + p.getType());
        System.out.println();
    }

    @Test
    @DisplayName("简单参数：计算器 — 混合类型 + @ParamDesc")
    void simpleParam_calculator() {
        ToolDefinition tool = registry.getTool("calculate");
        assertEquals(3, tool.getParameters().size());

        ToolParameter a = tool.getParameters().get(0);
        assertEquals("a", a.getName());
        assertEquals("number", a.getType());

        ToolParameter op = tool.getParameters().get(1);
        assertEquals("operator", op.getName());
        assertEquals("运算符：+、-、*、/", op.getDescription()); // @ParamDesc 生效

        ToolParameter b = tool.getParameters().get(2);
        assertEquals("b", b.getName());
        assertEquals("number", b.getType());

        System.out.println("=== 计算器 ===");
        for (ToolParameter p : tool.getParameters()) {
            System.out.println("参数: " + p.getName() + ", 类型: " + p.getType() + ", 描述: " + p.getDescription());
        }
        System.out.println();
    }

    @Test
    @DisplayName("无参数：查询时间")
    void noParam_time() {
        ToolDefinition tool = registry.getTool("getCurrentTime");
        assertEquals(0, tool.getParameters().size());
        System.out.println("=== 查询时间 ===");
        System.out.println("无参数，直接调用");
        System.out.println();
    }

    @Test
    @DisplayName("复杂嵌套类型：创建订单 — OrderRequest 含 List<OrderItem>")
    void complexParam_createOrder() throws Exception {
        ToolDefinition tool = registry.getTool("createOrder");
        assertEquals(1, tool.getParameters().size());

        ToolParameter p = tool.getParameters().get(0);
        assertEquals("order", p.getName());
        assertTrue(p.isComplex());
        assertNotNull(p.getSchema());

        // 打印生成的 JSON Schema，直观看效果
        String schema = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(p.getSchema());
        System.out.println("=== 创建订单 — 自动生成的 JSON Schema ===");
        System.out.println(schema);
        System.out.println();

        // 验证 schema 结构
        assertEquals("object", p.getSchema().get("type").asText());
        assertNotNull(p.getSchema().get("properties"));
        assertNotNull(p.getSchema().get("properties").get("customerName"));
        assertNotNull(p.getSchema().get("properties").get("shippingAddress"));
        assertNotNull(p.getSchema().get("properties").get("items"));

        // items 应该是 array 类型
        assertEquals("array", p.getSchema().get("properties").get("items").get("type").asText());

        // items 的元素应该是 object（OrderItem）
        assertNotNull(p.getSchema().get("properties").get("items").get("items"));
        assertEquals("object", p.getSchema().get("properties").get("items").get("items").get("type").asText());

        // OrderItem 应该有 productName, quantity, price
        assertNotNull(p.getSchema().get("properties").get("items").get("items").get("properties").get("productName"));
        assertNotNull(p.getSchema().get("properties").get("items").get("items").get("properties").get("quantity"));
        assertNotNull(p.getSchema().get("properties").get("items").get("items").get("properties").get("price"));
    }

    @Test
    @DisplayName("参数名不应该是 arg0 — 验证 -parameters 编译参数生效")
    void parameterNamesShouldNotBeArg0() {
        for (ToolDefinition tool : registry.getAllTools()) {
            for (ToolParameter p : tool.getParameters()) {
                assertFalse(p.getName().matches("arg\\d+"),
                        "参数名为 " + p.getName() + "，-parameters 编译参数可能未生效！");
            }
        }
        System.out.println("=== -parameters 检测通过 ===");
    }
}
