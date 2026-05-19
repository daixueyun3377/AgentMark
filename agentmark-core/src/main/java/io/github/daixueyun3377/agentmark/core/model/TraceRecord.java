package io.github.daixueyun3377.agentmark.core.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 完整的调用追踪记录，用于持久化到 JSON 文件。
 */
public class TraceRecord {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final String traceId;
    private final String requestTime;
    private final String userMessage;
    private final String reply;
    private final int llmCallCount;
    private final int toolCallCount;
    private final long totalDurationMs;
    private final long llmDurationMs;
    private final long toolDurationMs;
    private final List<CallRecord> callChain;

    public TraceRecord(String traceId, LocalDateTime requestTime, String userMessage,
                       String reply, ChatStatistics stats) {
        this.traceId = traceId;
        this.requestTime = requestTime.format(FMT);
        this.userMessage = userMessage;
        this.reply = reply;
        this.llmCallCount = stats.getLlmCallCount();
        this.toolCallCount = stats.getToolCallCount();
        this.totalDurationMs = stats.getTotalDurationMs();
        this.llmDurationMs = stats.getLlmDurationMs();
        this.toolDurationMs = stats.getToolDurationMs();
        this.callChain = stats.getCallChain();
    }

    public String getTraceId() { return traceId; }
    public String getRequestTime() { return requestTime; }
    public String getUserMessage() { return userMessage; }
    public String getReply() { return reply; }
    public int getLlmCallCount() { return llmCallCount; }
    public int getToolCallCount() { return toolCallCount; }
    public long getTotalDurationMs() { return totalDurationMs; }
    public long getLlmDurationMs() { return llmDurationMs; }
    public long getToolDurationMs() { return toolDurationMs; }
    public List<CallRecord> getCallChain() { return Collections.unmodifiableList(callChain); }
}
