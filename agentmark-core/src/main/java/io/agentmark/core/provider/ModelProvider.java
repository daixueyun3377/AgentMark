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
     */
    ChatResponse chat(String userMessage, Collection<ToolDefinition> tools, List<ChatMessage> history);

    /**
     * 将工具调用结果反馈给模型，获取最终回复。
     */
    ChatResponse submitToolResults(List<ChatMessage> history, Collection<ToolDefinition> tools);

    /** 聊天消息 */
    class ChatMessage {
        private final String role;
        private final String content;
        private final List<ToolCall> toolCalls;
        private final String toolCallId;

        public ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {
            this.role = role;
            this.content = content;
            this.toolCalls = toolCalls;
            this.toolCallId = toolCallId;
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content, null, null);
        }

        public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
            return new ChatMessage("assistant", content, toolCalls, null);
        }

        public static ChatMessage toolResult(String toolCallId, String content) {
            return new ChatMessage("tool", content, null, toolCallId);
        }

        public String role() { return role; }
        public String content() { return content; }
        public List<ToolCall> toolCalls() { return toolCalls; }
        public String toolCallId() { return toolCallId; }
    }

    /** 工具调用请求 */
    class ToolCall {
        private final String id;
        private final String name;
        private final Map<String, Object> arguments;

        public ToolCall(String id, String name, Map<String, Object> arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }

        public String id() { return id; }
        public String name() { return name; }
        public Map<String, Object> arguments() { return arguments; }
    }

    /** 模型响应 */
    class ChatResponse {
        private final String text;
        private final List<ToolCall> toolCalls;

        public ChatResponse(String text, List<ToolCall> toolCalls) {
            this.text = text;
            this.toolCalls = toolCalls;
        }

        public String text() { return text; }
        public List<ToolCall> toolCalls() { return toolCalls; }

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
