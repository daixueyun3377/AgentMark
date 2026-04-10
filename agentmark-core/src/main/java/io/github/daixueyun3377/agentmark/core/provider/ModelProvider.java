package io.github.daixueyun3377.agentmark.core.provider;

import io.github.daixueyun3377.agentmark.core.model.ToolDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 模型提供者接口，对接不同的 LLM API。
 */
public interface ModelProvider {

    ChatResponse chat(String userMessage, Collection<ToolDefinition> tools, List<ChatMessage> history);

    ChatResponse submitToolResults(List<ChatMessage> history, Collection<ToolDefinition> tools);

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

        public String getRole() { return role; }
        public String getContent() { return content; }
        public List<ToolCall> getToolCalls() { return toolCalls; }
        public String getToolCallId() { return toolCallId; }
    }

    class ToolCall {
        private final String id;
        private final String name;
        private final Map<String, Object> arguments;

        public ToolCall(String id, String name, Map<String, Object> arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public Map<String, Object> getArguments() { return arguments; }
    }

    class ChatResponse {
        private final String text;
        private final List<ToolCall> toolCalls;

        public ChatResponse(String text, List<ToolCall> toolCalls) {
            this.text = text;
            this.toolCalls = toolCalls;
        }

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }

        public String getText() { return text; }
        public List<ToolCall> getToolCalls() { return toolCalls; }
    }
}
