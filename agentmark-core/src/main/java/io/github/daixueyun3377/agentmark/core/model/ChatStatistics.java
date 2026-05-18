package io.github.daixueyun3377.agentmark.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单次 chat 调用的统计信息。
 */
public class ChatStatistics {

    private int llmCallCount;
    private int toolCallCount;
    private long totalDurationMs;
    private long llmDurationMs;
    private long toolDurationMs;
    private final List<CallRecord> callChain = new ArrayList<>();

    public void recordLlmCall(long startTime, long durationMs, boolean success, String error) {
        llmCallCount++;
        llmDurationMs += durationMs;
        callChain.add(CallRecord.llm(startTime, durationMs, success, error));
    }

    public void recordToolCall(String toolName, long startTime, long durationMs, boolean success, String error) {
        toolCallCount++;
        toolDurationMs += durationMs;
        callChain.add(CallRecord.tool(toolName, startTime, durationMs, success, error));
    }

    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public int getLlmCallCount() { return llmCallCount; }
    public int getToolCallCount() { return toolCallCount; }
    public long getTotalDurationMs() { return totalDurationMs; }
    public long getLlmDurationMs() { return llmDurationMs; }
    public long getToolDurationMs() { return toolDurationMs; }
    public List<CallRecord> getCallChain() { return Collections.unmodifiableList(callChain); }

    @Override
    public String toString() {
        return String.format("ChatStatistics{llm=%d(%dms), tool=%d(%dms), total=%dms, chain=%d steps}",
                llmCallCount, llmDurationMs, toolCallCount, toolDurationMs, totalDurationMs, callChain.size());
    }
}
