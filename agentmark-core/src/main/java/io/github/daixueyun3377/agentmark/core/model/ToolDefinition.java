package io.github.daixueyun3377.agentmark.core.model;

import java.util.List;

/**
 * 工具定义，描述一个可被 AI 调用的工具。
 */
public class ToolDefinition {

    private final String name;
    private final String description;
    private final List<ToolParameter> parameters;
    private final Object targetBean;
    private final java.lang.reflect.Method targetMethod;

    public ToolDefinition(String name, String description, List<ToolParameter> parameters,
                          Object targetBean, java.lang.reflect.Method targetMethod) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
        this.targetBean = targetBean;
        this.targetMethod = targetMethod;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<ToolParameter> getParameters() { return parameters; }
    public Object getTargetBean() { return targetBean; }
    public java.lang.reflect.Method getTargetMethod() { return targetMethod; }
}
