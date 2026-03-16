package io.agentmark.core.provider;

import io.agentmark.core.model.ToolDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 模型提供者接口，对接不同的 LLM API。
 */
public interface ModelProvider {

    /**
     * 发送用户消息和可用工具列表，返回模型响应。
     * 如果模型决定调用工具，返回 ToolCall 列表；否则返回文本回复。
     */
    ChatResponse chat(String userMessage, Collection<ToolDefinition> tools, List<ChatMessage> history);

    /**
     * 将工具调用结果反馈给模型，获取最终回复。
     */
    ChatResponse submitToolResults(List<ChatMessage> history, Collection<ToolDefinition> tools);

    /** 聊天消息 */
    record ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {
        public static ChatMessage user(String content) {
            return new ChatMessage("user", content, null, null);
        }
        public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
            return new ChatMessage("assistant", content, toolCalls, null);
        }
        public static ChatMessage toolResult(String toolCallId, String content) {
            return new ChatMessage("tool", content, null, toolCallId);
        }
    }

    /** 工具调用请求 */
    record ToolCall(String id, String name, Map<String, Object> arguments) {}

    /** 模型响应 */
    record ChatResponse(String text, List<ToolCall> toolCalls) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
