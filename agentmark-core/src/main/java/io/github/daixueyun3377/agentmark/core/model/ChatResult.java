package io.github.daixueyun3377.agentmark.core.model;

/**
 * chat() 的返回结果，包含回复文本和调用统计信息。
 */
public class ChatResult {

    private final String text;
    private final ChatStatistics statistics;

    public ChatResult(String text, ChatStatistics statistics) {
        this.text = text;
        this.statistics = statistics;
    }

    public String getText() { return text; }
    public ChatStatistics getStatistics() { return statistics; }

    @Override
    public String toString() {
        return String.format("ChatResult{text=%s, stats=%s}",
                text != null && text.length() > 50 ? text.substring(0, 50) + "..." : text,
                statistics);
    }
}
