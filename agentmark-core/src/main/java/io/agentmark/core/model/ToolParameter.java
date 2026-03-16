package io.agentmark.core.model;

/**
 * 工具参数定义。
 */
public class ToolParameter {

    private final String name;
    private final String description;
    private final String type;
    private final boolean required;

    public ToolParameter(String name, String description, String type, boolean required) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.required = required;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public boolean isRequired() { return required; }
}
