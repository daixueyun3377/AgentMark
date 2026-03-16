package io.agentmark.core.agent;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentmark.core.model.ToolDefinition;
import io.agentmark.core.model.ToolResult;
import io.agentmark.core.provider.ModelProvider;
import io.agentmark.core.provider.ModelProvider.*;
import io.agentmark.core.registry.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * AgentMark 核心 Agent，处理用户消息并自动调用工具。
 * 支持复杂嵌套类型的参数自动转换。
 */
public class AgentMarkAgent {

    private static final Logger log = LoggerFactory.getLogger(AgentMarkAgent.class);
    private static final int MAX_TOOL_ROUNDS = 10;

    private final ToolRegistry registry;
    private final ModelProvider provider;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentMarkAgent(ToolRegistry registry, ModelProvider provider) {
        this.registry = registry;
        this.provider = provider;
    }

    /**
     * 单轮对话（无上下文）。
     */
    public String chat(String userMessage) {
        return newSession().chat(userMessage);
    }

    /**
     * 创建带上下文的会话。
     */
    public AgentMarkSession newSession() {
        return new AgentMarkSession(this);
    }

    String processMessage(String userMessage, List<ChatMessage> history) {
        ChatResponse response = provider.chat(userMessage, registry.getAllTools(), history);
        history.add(ChatMessage.user(userMessage));

        int rounds = 0;
        while (response.hasToolCalls() && rounds < MAX_TOOL_ROUNDS) {
            rounds++;
            history.add(ChatMessage.assistant(response.text(), response.toolCalls()));

            for (ToolCall toolCall : response.toolCalls()) {
                ToolResult result = executeTool(toolCall);
                String resultJson = toJson(result);
                history.add(ChatMessage.toolResult(toolCall.id(), resultJson));
            }

            response = provider.submitToolResults(history, registry.getAllTools());
        }

        String finalText = response.text() != null ? response.text() : "";
        history.add(ChatMessage.assistant(finalText, null));
        return finalText;
    }

    private ToolResult executeTool(ToolCall toolCall) {
        ToolDefinition tool = registry.getTool(toolCall.name());
        if (tool == null) {
            return ToolResult.failure(toolCall.name(), "Tool not found: " + toolCall.name());
        }

        try {
            Method method = tool.getTargetMethod();
            Parameter[] params = method.getParameters();
            Type[] genericTypes = method.getGenericParameterTypes();
            Object[] args = new Object[params.length];

            for (int i = 0; i < params.length; i++) {
                String paramName = params[i].getName();
                Object value = toolCall.arguments().get(paramName);
                args[i] = convertArg(value, params[i].getType(), genericTypes[i]);
            }

            Object result = method.invoke(tool.getTargetBean(), args);
            log.info("Tool [{}] executed successfully", toolCall.name());
            return ToolResult.success(toolCall.name(), result);
        } catch (Exception e) {
            log.error("Tool [{}] execution failed", toolCall.name(), e);
            return ToolResult.failure(toolCall.name(), e.getMessage());
        }
    }

    /**
     * 参数转换：支持简单类型和复杂嵌套类型。
     * 复杂类型通过 Jackson 进行反序列化。
     */
    private Object convertArg(Object value, Class<?> targetType, Type genericType) {
        if (value == null) return null;

        // 简单类型直接转换
        if (targetType == String.class) return value.toString();
        if (targetType == int.class || targetType == Integer.class) return toInt(value);
        if (targetType == long.class || targetType == Long.class) return toLong(value);
        if (targetType == double.class || targetType == Double.class) return toDouble(value);
        if (targetType == float.class || targetType == Float.class) return toFloat(value);
        if (targetType == boolean.class || targetType == Boolean.class) return toBoolean(value);
        if (targetType == short.class || targetType == Short.class) return toShort(value);
        if (targetType == byte.class || targetType == Byte.class) return toByte(value);

        // 复杂类型：用 Jackson 转换（支持嵌套对象、List、Map 等）
        try {
            JavaType javaType = mapper.getTypeFactory().constructType(genericType);
            return mapper.convertValue(value, javaType);
        } catch (Exception e) {
            log.warn("Failed to convert arg to {}, trying direct cast", targetType.getSimpleName(), e);
            return value;
        }
    }

    private int toInt(Object v) { return Integer.parseInt(v.toString()); }
    private long toLong(Object v) { return Long.parseLong(v.toString()); }
    private double toDouble(Object v) { return Double.parseDouble(v.toString()); }
    private float toFloat(Object v) { return Float.parseFloat(v.toString()); }
    private boolean toBoolean(Object v) { return Boolean.parseBoolean(v.toString()); }
    private short toShort(Object v) { return Short.parseShort(v.toString()); }
    private byte toByte(Object v) { return Byte.parseByte(v.toString()); }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
