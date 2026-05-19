package io.github.daixueyun3377.agentmark.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.daixueyun3377.agentmark.core.model.*;
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider;
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider.*;
import io.github.daixueyun3377.agentmark.core.registry.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AgentMark 核心 Agent，处理用户消息并自动调用工具。
 */
public class AgentMarkAgent {

    private static final Logger log = LoggerFactory.getLogger(AgentMarkAgent.class);
    private static final int MAX_TOOL_ROUNDS = 10;

    private final ToolRegistry registry;
    private final ModelProvider provider;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public AgentMarkAgent(ToolRegistry registry, ModelProvider provider) {
        this.registry = registry;
        this.provider = provider;
    }

    /**
     * 单轮对话（无上下文），返回包含回复文本和统计信息的结果。
     */
    public ChatResult chat(String userMessage) {
        return newSession().chat(userMessage);
    }

    /**
     * 创建带上下文的会话。
     */
    public AgentMarkSession newSession() {
        return new AgentMarkSession(this);
    }

    ChatResult processMessage(String userMessage, List<ChatMessage> history) {
        ChatStatistics stats = new ChatStatistics();
        long totalStart = System.currentTimeMillis();

        // 首次 LLM 调用
        long llmStart = System.currentTimeMillis();
        ChatResponse response;
        try {
            response = provider.chat(userMessage, registry.getAllTools(), history);
            stats.recordLlmCall(llmStart, System.currentTimeMillis() - llmStart, true, null,
                    userMessage, buildLlmOutput(response));
        } catch (Exception e) {
            stats.recordLlmCall(llmStart, System.currentTimeMillis() - llmStart, false, e.getMessage(),
                    userMessage, null);
            stats.setTotalDurationMs(System.currentTimeMillis() - totalStart);
            return new ChatResult("", stats);
        }

        history.add(ChatMessage.user(userMessage));

        int rounds = 0;
        while (response.hasToolCalls() && rounds < MAX_TOOL_ROUNDS) {
            rounds++;
            history.add(ChatMessage.assistant(response.getText(), response.getToolCalls()));

            List<Map<String, Object>> toolResultsSummary = new ArrayList<>();
            for (ToolCall toolCall : response.getToolCalls()) {
                long toolStart = System.currentTimeMillis();
                ToolResult result = executeTool(toolCall);
                long toolDuration = System.currentTimeMillis() - toolStart;
                stats.recordToolCall(toolCall.getName(), toolStart, toolDuration,
                        result.isSuccess(), result.getError(),
                        toolCall.getArguments(), result.getData());

                Map<String, Object> toolSummary = new java.util.LinkedHashMap<>();
                toolSummary.put("tool", toolCall.getName());
                toolSummary.put("result", result.getData());
                toolResultsSummary.add(toolSummary);

                String resultJson = toJson(result);
                history.add(ChatMessage.toolResult(toolCall.getId(), resultJson));
            }

            // 后续 LLM 调用（提交工具结果）
            llmStart = System.currentTimeMillis();
            try {
                response = provider.submitToolResults(history, registry.getAllTools());
                stats.recordLlmCall(llmStart, System.currentTimeMillis() - llmStart, true, null,
                        toolResultsSummary, buildLlmOutput(response));
            } catch (Exception e) {
                stats.recordLlmCall(llmStart, System.currentTimeMillis() - llmStart, false, e.getMessage(),
                        toolResultsSummary, null);
                stats.setTotalDurationMs(System.currentTimeMillis() - totalStart);
                return new ChatResult("", stats);
            }
        }

        String finalText = response.getText() != null ? response.getText() : "";
        history.add(ChatMessage.assistant(finalText, null));

        stats.setTotalDurationMs(System.currentTimeMillis() - totalStart);
        log.info("Chat completed: {}", stats);
        return new ChatResult(finalText, stats);
    }

    /**
     * 构建 LLM 输出摘要，包含响应文本和工具调用决策。
     */
    private Map<String, Object> buildLlmOutput(ChatResponse response) {
        Map<String, Object> output = new java.util.LinkedHashMap<>();
        output.put("text", response.getText());
        if (response.hasToolCalls()) {
            List<Map<String, Object>> calls = new ArrayList<>();
            for (ToolCall tc : response.getToolCalls()) {
                Map<String, Object> call = new java.util.LinkedHashMap<>();
                call.put("tool", tc.getName());
                call.put("arguments", tc.getArguments());
                calls.add(call);
            }
            output.put("toolCalls", calls);
        }
        return output;
    }

    private ToolResult executeTool(ToolCall toolCall) {
        ToolDefinition tool = registry.getTool(toolCall.getName());
        if (tool == null) {
            return ToolResult.failure(toolCall.getName(), "Tool not found: " + toolCall.getName());
        }

        try {
            Method method = tool.getTargetMethod();
            Parameter[] params = method.getParameters();
            Object[] args = new Object[params.length];

            for (int i = 0; i < params.length; i++) {
                String paramName = params[i].getName();
                Object value = toolCall.getArguments().get(paramName);
                args[i] = convertArg(value, params[i].getType());
            }

            Object result = method.invoke(tool.getTargetBean(), args);
            log.info("Tool [{}] executed successfully", toolCall.getName());
            return ToolResult.success(toolCall.getName(), result);
        } catch (Exception e) {
            log.error("Tool [{}] execution failed", toolCall.getName(), e);
            return ToolResult.failure(toolCall.getName(), e.getMessage());
        }
    }

    private Object convertArg(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType == String.class) return value.toString();
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value.toString());
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(value.toString());
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value.toString());
        if (targetType == float.class || targetType == Float.class) return Float.parseFloat(value.toString());
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value.toString());
        // 复杂类型用 Jackson 反序列化
        try {
            return mapper.convertValue(value, targetType);
        } catch (Exception e) {
            log.warn("Failed to convert arg to {}: {}", targetType.getSimpleName(), e.getMessage());
            return value;
        }
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
