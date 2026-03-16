package io.agentmark.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 工具参数定义，支持简单类型和复杂嵌套类型。
 */
public class ToolParameter {

    private final String name;
    private final String description;
    private final String type;
    private final boolean required;
    /** 完整的 JSON Schema 节点，用于复杂嵌套类型 */
    private final ObjectNode schema;

    public ToolParameter(String name, String description, String type, boolean required) {
        this(name, description, type, required, null);
    }

    public ToolParameter(String name, String description, String type, boolean required, ObjectNode schema) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.required = required;
        this.schema = schema;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public boolean isRequired() { return required; }
    public ObjectNode getSchema() { return schema; }

    /** 是否为复杂类型（有完整 schema） */
    public boolean isComplex() { return schema != null; }
}
