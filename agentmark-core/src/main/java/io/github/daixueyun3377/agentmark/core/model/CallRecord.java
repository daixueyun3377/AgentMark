package io.github.daixueyun3377.agentmark.core.model;

/**
 * 调用链中的单条记录，记录一次 LLM 或工具调用的详情。
 */
public class CallRecord {

    private final String type;
    private final String name;
    private final long startTime;
    private final long durationMs;
    private final boolean success;
    private final String error;
    private final Object input;
    private final Object output;

    public CallRecord(String type, String name, long startTime, long durationMs,
                      boolean success, String error, Object input, Object output) {
        this.type = type;
        this.name = name;
        this.startTime = startTime;
        this.durationMs = durationMs;
        this.success = success;
        this.error = error;
        this.input = input;
        this.output = output;
    }

    public static CallRecord llm(long startTime, long durationMs, boolean success,
                                   String error, Object input, Object output) {
        return new CallRecord("llm", "llm", startTime, durationMs, success, error, input, output);
    }

    public static CallRecord tool(String toolName, long startTime, long durationMs,
                                  boolean success, String error, Object input, Object output) {
        return new CallRecord("tool", toolName, startTime, durationMs, success, error, input, output);
    }

    public String getType() { return type; }
    public String getName() { return name; }
    public long getStartTime() { return startTime; }
    public long getDurationMs() { return durationMs; }
    public boolean isSuccess() { return success; }
    public String getError() { return error; }
    public Object getInput() { return input; }
    public Object getOutput() { return output; }

    @Override
    public String toString() {
        return String.format("[%s] %s %dms %s", type, name, durationMs, success ? "OK" : "FAIL:" + error);
    }
}
