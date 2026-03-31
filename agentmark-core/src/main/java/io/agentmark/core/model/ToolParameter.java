package io.agentmark.core.model;

import java.util.List;

/**
 * 工具参数定义，支持嵌套对象和数组类型。
 */
public class ToolParameter {

    private final String name;
    private final String description;
    private final String type;
    private final boolean required;

    /** 当 type 为 "object" 时，描述嵌套字段 */
    private final List<ToolParameter> properties;

    /** 当 type 为 "array" 时，描述数组元素 */
    private final ToolParameter items;

    /** 枚举值列表（可选） */
    private final List<String> enumValues;

    public ToolParameter(String name, String description, String type, boolean required) {
        this(name, description, type, required, null, null, null);
    }

    public ToolParameter(String name, String description, String type, boolean required,
                         List<ToolParameter> properties, ToolParameter items, List<String> enumValues) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.required = required;
        this.properties = properties;
        this.items = items;
        this.enumValues = enumValues;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public boolean isRequired() { return required; }
    public List<ToolParameter> getProperties() { return properties; }
    public ToolParameter getItems() { return items; }
    public List<String> getEnumValues() { return enumValues; }
}
