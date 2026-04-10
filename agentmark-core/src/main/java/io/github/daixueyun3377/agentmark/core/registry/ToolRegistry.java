package io.github.daixueyun3377.agentmark.core.registry;

import io.github.daixueyun3377.agentmark.core.annotation.ParamDesc;
import io.github.daixueyun3377.agentmark.core.annotation.AgentMark;
import io.github.daixueyun3377.agentmark.core.model.ToolDefinition;
import io.github.daixueyun3377.agentmark.core.model.ToolParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心，自动扫描并注册 @AgentMark 标记的方法。
 * 支持递归解析 POJO 字段上的 @ParamDesc 注解，生成嵌套 JSON Schema。
 */
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    /**
     * 扫描对象中所有 @AgentMark 标记的方法并注册。
     */
    public void register(Object bean) {
        for (Method method : bean.getClass().getMethods()) {
            AgentMark tool = method.getAnnotation(AgentMark.class);
            if (tool == null) continue;

            List<ToolParameter> params = new ArrayList<>();
            for (Parameter param : method.getParameters()) {
                params.add(resolveParameter(param));
            }

            String toolName = tool.name().isEmpty() ? method.getName() : tool.name();
            String toolDesc = tool.description().isEmpty() ? "" : tool.description();

            ToolDefinition def = new ToolDefinition(toolName, toolDesc, params, bean, method);
            tools.put(toolName, def);
            log.info("Registered tool: {} -> {}.{}", toolName, bean.getClass().getSimpleName(), method.getName());
        }
    }

    public ToolDefinition getTool(String name) {
        return tools.get(name);
    }

    public Collection<ToolDefinition> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * 解析方法参数，如果是复杂类型则递归解析字段。
     */
    private ToolParameter resolveParameter(Parameter param) {
        ParamDesc p = param.getAnnotation(ParamDesc.class);
        String desc = (p != null) ? p.value() : param.getName();
        boolean required = (p != null) ? p.required() : true;
        Class<?> type = param.getType();
        Type genericType = param.getParameterizedType();

        return buildParameter(param.getName(), desc, type, genericType, required, new HashSet<>());
    }

    /**
     * 递归构建参数定义，支持嵌套对象、集合、枚举等。
     * visited 用于检测循环引用。
     */
    private ToolParameter buildParameter(String name, String desc, Class<?> type,
                                         Type genericType, boolean required, Set<Class<?>> visited) {
        // 基本类型
        String jsonType = mapSimpleType(type);
        if (jsonType != null) {
            return new ToolParameter(name, desc, jsonType, required);
        }

        // 枚举
        if (type.isEnum()) {
            List<String> values = new ArrayList<>();
            for (Object c : type.getEnumConstants()) {
                values.add(c.toString());
            }
            return new ToolParameter(name, desc, "string", required, null, null, values);
        }

        // 数组
        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            ToolParameter items = buildParameter("item", "", componentType, componentType, true, visited);
            return new ToolParameter(name, desc, "array", required, null, items, null);
        }

        // List / Set
        if (List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type)) {
            ToolParameter items = resolveGenericItem(genericType, visited);
            return new ToolParameter(name, desc, "array", required, null, items, null);
        }

        // Map
        if (Map.class.isAssignableFrom(type)) {
            return new ToolParameter(name, desc, "object", required);
        }

        // POJO — 递归解析字段
        if (!visited.add(type)) {
            // 循环引用，停止递归
            log.warn("Circular reference detected for type: {}", type.getName());
            return new ToolParameter(name, desc, "object", required);
        }

        List<ToolParameter> properties = new ArrayList<>();
        for (Field field : getAllFields(type)) {
            field.setAccessible(true);
            ParamDesc fp = field.getAnnotation(ParamDesc.class);
            String fieldDesc = (fp != null) ? fp.value() : field.getName();
            boolean fieldRequired = (fp != null) ? fp.required() : true;
            ToolParameter fieldParam = buildParameter(
                    field.getName(), fieldDesc, field.getType(),
                    field.getGenericType(), fieldRequired, new HashSet<>(visited));
            properties.add(fieldParam);
        }

        visited.remove(type);
        return new ToolParameter(name, desc, "object", required, properties, null, null);
    }

    private ToolParameter resolveGenericItem(Type genericType, Set<Class<?>> visited) {
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] typeArgs = pt.getActualTypeArguments();
            // List<T> / Set<T> 取第一个类型参数
            if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                Class<?> itemClass = (Class<?>) typeArgs[0];
                return buildParameter("item", "", itemClass, itemClass, true, visited);
            }
        }
        // 无法推断泛型，回退为 string
        return new ToolParameter("item", "", "string", true);
    }

    /**
     * 获取类及其所有父类的声明字段（不含 static/transient）。
     */
    private List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) continue;
                fields.add(f);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    /**
     * 映射 Java 简单类型到 JSON Schema 类型，复杂类型返回 null。
     */
    private String mapSimpleType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class) return "integer";
        if (type == long.class || type == Long.class) return "integer";
        if (type == double.class || type == Double.class) return "number";
        if (type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type == BigDecimal.class) return "number";
        if (type == java.util.Date.class) return "string";
        if (type == LocalDate.class) return "string";
        if (type == LocalDateTime.class) return "string";
        if (type == LocalTime.class) return "string";
        if (type == java.time.Instant.class) return "string";
        if (type == java.time.OffsetDateTime.class) return "string";
        if (type == java.time.ZonedDateTime.class) return "string";
        return null;
    }
}
