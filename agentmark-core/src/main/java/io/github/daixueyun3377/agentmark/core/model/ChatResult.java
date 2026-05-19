package io.github.daixueyun3377.agentmark.core.model;

/**
 * chat() 的返回结果，包含回复文本和 traceId。
 * 当 trace 开启时，traceId 可用于关联存储的调用链 JSON 文件。
 */
public class ChatResult {

    private final String text;
    private final String traceId;

    public ChatResult(String text, String traceId) {
        this.text = text;
        this.traceId = traceId;
    }

    public String getText() { return text; }
    public String getTraceId() { return traceId; }

    @Override
    public String toString() {
        return text != null ? text : "";
    }
}
