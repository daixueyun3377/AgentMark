package io.agentmark.core.model;

/**
 * 工具调用结果。
 */
public class ToolResult {

    private final String toolName;
    private final boolean success;
    private final Object data;
    private final String error;

    public static ToolResult success(String toolName, Object data) {
        return new ToolResult(toolName, true, data, null);
    }

    public static ToolResult failure(String toolName, String error) {
        return new ToolResult(toolName, false, null, error);
    }

    private ToolResult(String toolName, boolean success, Object data, String error) {
        this.toolName = toolName;
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public String getToolName() { return toolName; }
    public boolean isSuccess() { return success; }
    public Object getData() { return data; }
    public String getError() { return error; }
}
