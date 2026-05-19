package io.github.daixueyun3377.agentmark.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.daixueyun3377.agentmark.core.model.*;
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider;
import io.github.daixueyun3377.agentmark.core.provider.ModelProvider.*;
import io.github.daixueyun3377.agentmark.core.registry.ToolRegistry;
import io.github.daixueyun3377.agentmark.core.trace.TraceWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AgentMark 核心 Agent，处理用户消息并自动调用工具。
 */
public class AgentMarkAgent {

    private static final Logger log = LoggerFactory.getLogger(AgentMarkAgent.class);
    private static final int DEFAULT_MAX_TOOL_ROUNDS = 10;

    private final ToolRegistry registry;
    private final ModelProvider provider;
    private final TraceWriter traceWriter;
    private final String systemPrompt;
    private final int maxToolRounds;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public AgentMarkAgent(ToolRegistry registry, ModelProvider provider) {
        this(registry, provider, null, null, DEFAULT_MAX_TOOL_ROUNDS);
    }

    public AgentMarkAgent(ToolRegistry registry, ModelProvider provider, TraceWriter traceWriter) {
        this(registry, provider, traceWriter, null, DEFAULT_MAX_TOOL_ROUNDS);
    }

    public AgentMarkAgent(ToolRegistry registry, ModelProvider provider, TraceWriter traceWriter, String systemPrompt) {
        this(registry, provider, traceWriter, systemPrompt, DEFAULT_MAX_TOOL_ROUNDS);
    }

    public AgentMarkAgent(ToolRegistry registry, ModelProvider provider, TraceWriter traceWriter, String systemPrompt, int maxToolRounds) {
        this.registry = registry;
        this.provider = provider;
        this.traceWriter = traceWriter;
        this.systemPrompt = systemPrompt;
        this.maxToolRounds = maxToolRounds;
    }

    /**
     * 单轮对话（无上下文），返回包含回复文本和 traceId 的结果。
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
        boolean tracing = traceWriter != null;
        ChatStatistics stats = tracing ? new ChatStatistics() : null;
        String traceId = tracing ? generateTraceId() : null;
        LocalDateTime requestTime = tracing ? LocalDateTime.now() : null;
        long totalStart = tracing ? System.currentTimeMillis() : 0;

        // 注入 system prompt（仅首次，避免重复添加）
        if (systemPrompt != null && !systemPrompt.isEmpty() && history.isEmpty()) {
            history.add(ChatMessage.system(systemPrompt));
        }

        // 首次 LLM 调用
        long llmStart = tracing ? System.currentTimeMillis() : 0;
        ChatResponse response;
        try {
            response = provider.chat(userMessage, registry.getAllTools(), history);
            if (tracing) {
                stats.recordLlmCall(llmStart, System.currentTimeMillis() - llmStart, true, null,
                        userMessage, buildLlmOutput(response));
            }
        } catch (Exception e) {
            if (tracing) {
                stats.recordLlmCall(llmStart, System.currentTimeMillis() - llmStart, false, e.getMessage(),
                        userMessage, null);
                stats.setTotalDurationMs(System.currentTimeMillis() - totalStart);
                writeTrace(traceId, requestTime, userMessage, "", stats);
            }
            return new ChatResult("", traceId);
        }

        history.add(ChatMessage.user(userMessage));

        int rounds = 0;
        while (response.hasToolCalls() && rounds < maxToolRounds) {
            rounds++;
            history.add(ChatMessage.assistant(response.getText(), response.getToolCalls()));

            List<Map<String, Object>> toolResultsSummary = tracing ? new ArrayList<>() : null;
            for (ToolCall toolCall : response.getToolCalls()) {
                long toolStart = tracing ? System.currentTimeMillis() : 0;
                ToolResult result = executeTool(toolCall);

                if (tracing) {
                    long toolDuration = System.currentTimeMillis() - toolStart;
                    stats.recordToolCall(toolCall.getName(), toolStart, toolDuration,
                            result.isSuccess(), result.getError(),
                            toolCall.getArguments(), result.getData());

                    Map<String, Object> toolSummary = new LinkedHashMap<>();
                    toolSummary.put("tool", toolCall.getName());
                    toolSummary.put("result", result.getData());
                    toolResultsSummary.add(toolSummary);
                }

                String resultJson = toJson(result);
                history.add(ChatMessage.toolResult(toolCall.getId(), resultJson));
            }

            // 后续 LLM 调用（提交工具结果）
            llmStart = tracing ? System.currentTimeMillis() : 0;
            try {
                response = provider.submitToolResults(history, registry.getAllTools());
                if (tracing) {
                    stats.recordLlmCall(llmStart, System.currentTimeMillis() - llmStart, true, null,
                            toolResultsSummary, buildLlmOutput(response));
                }
            } catch (Exception e) {
                if (tracing) {
                    stats.recordLlmCall(llmStart, System.currentTimeMillis() - llmStart, false, e.getMessage(),
                            toolResultsSummary, null);
                    stats.setTotalDurationMs(System.currentTimeMillis() - totalStart);
                    writeTrace(traceId, requestTime, userMessage, "", stats);
                }
                return new ChatResult("", traceId);
            }
        }

        String finalText = response.getText() != null ? response.getText() : "";
        history.add(ChatMessage.assistant(finalText, null));

        if (tracing) {
            stats.setTotalDurationMs(System.currentTimeMillis() - totalStart);
            writeTrace(traceId, requestTime, userMessage, finalText, stats);
            log.info("Chat completed [traceId={}]: llm={}, tool={}, total={}ms",
                    traceId, stats.getLlmCallCount(), stats.getToolCallCount(), stats.getTotalDurationMs());
        }

        return new ChatResult(finalText, traceId);
    }

    private void writeTrace(String traceId, LocalDateTime requestTime,
                            String userMessage, String reply, ChatStatistics stats) {
        try {
            TraceRecord record = new TraceRecord(traceId, requestTime, userMessage, reply, stats);
            traceWriter.write(record);
        } catch (Exception e) {
            log.error("Failed to write trace [{}]", traceId, e);
        }
    }

    private Map<String, Object> buildLlmOutput(ChatResponse response) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("text", response.getText());
        if (response.hasToolCalls()) {
            List<Map<String, Object>> calls = new ArrayList<>();
            for (ToolCall tc : response.getToolCalls()) {
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("tool", tc.getName());
                call.put("arguments", tc.getArguments());
                calls.add(call);
            }
            output.put("toolCalls", calls);
        }
        return output;
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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
