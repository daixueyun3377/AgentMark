package io.github.daixueyun3377.agentmark.core.agent;

import io.github.daixueyun3377.agentmark.core.provider.ModelProvider.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 带上下文记忆的会话，支持多轮对话。
 */
public class AgentMarkSession {

    private final AgentMarkAgent agent;
    private final List<ChatMessage> history = new ArrayList<>();

    AgentMarkSession(AgentMarkAgent agent) {
        this.agent = agent;
    }

    /**
     * 发送消息并获取回复，自动保持上下文。
     */
    public String chat(String userMessage) {
        return agent.processMessage(userMessage, history);
    }

    /**
     * 清除对话历史。
     */
    public void clear() {
        history.clear();
    }
}
