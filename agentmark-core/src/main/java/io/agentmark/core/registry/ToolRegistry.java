package io.agentmark.core.registry;

import io.agentmark.core.annotation.P;
import io.agentmark.core.annotation.Tool;
import io.agentmark.core.model.ToolDefinition;
import io.agentmark.core.model.ToolParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心，自动扫描并注册 @Tool 标记的方法。
 */
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    /**
     * 扫描对象中所有 @Tool 标记的方法并注册。
     */
    public void register(Object bean) {
        for (Method method : bean.getClass().getMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) continue;

            List<ToolParameter> params = new ArrayList<>();
            for (Parameter param : method.getParameters()) {
                P p = param.getAnnotation(P.class);
                String desc = (p != null) ? p.value() : param.getName();
                boolean required = (p != null) ? p.required() : true;
                String type = mapJavaType(param.getType());
                params.add(new ToolParameter(param.getName(), desc, type, required));
            }

            ToolDefinition def = new ToolDefinition(tool.name(), tool.description(), params, bean, method);
            tools.put(tool.name(), def);
            log.info("Registered tool: {} -> {}.{}", tool.name(), bean.getClass().getSimpleName(), method.getName());
        }
    }

    public ToolDefinition getTool(String name) {
        return tools.get(name);
    }

    public Collection<ToolDefinition> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    private String mapJavaType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class) return "integer";
        if (type == long.class || type == Long.class) return "integer";
        if (type == double.class || type == Double.class) return "number";
        if (type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        return "string";
    }
}
