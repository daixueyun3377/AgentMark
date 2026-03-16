package io.agentmark.core.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentmark.core.annotation.AgentMark;
import io.agentmark.core.annotation.ParamDesc;
import io.agentmark.core.model.ToolDefinition;
import io.agentmark.core.model.ToolParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心，自动扫描并注册 @AgentMark 标记的方法。
 * 支持复杂嵌套类型的自动 JSON Schema 推断。
 */
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    /**
     * 扫描对象中所有 @AgentMark 标记的方法并注册。
     */
    public void register(Object bean) {
        for (Method method : bean.getClass().getMethods()) {
            AgentMark mark = method.getAnnotation(AgentMark.class);
            if (mark == null) continue;

            // tool name 必须是 [a-zA-Z0-9_-]，Claude/OpenAI API 不支持中文
            // 策略：始终用方法名作为 tool name，中文名放到 description 里
            String toolName = method.getName();
            String userLabel = (mark.name() != null && !mark.name().isEmpty()) ? mark.name() : "";
            String userDesc = (mark.description() != null && !mark.description().isEmpty())
                    ? mark.description() : "";
            String toolDesc;
            if (!userLabel.isEmpty() && !userDesc.isEmpty()) {
                toolDesc = userLabel + " - " + userDesc;
            } else if (!userDesc.isEmpty()) {
                toolDesc = userDesc;
            } else if (!userLabel.isEmpty()) {
                toolDesc = userLabel;
            } else {
                toolDesc = "方法: " + method.getName();
            }

            List<ToolParameter> params = new ArrayList<>();
            Parameter[] methodParams = method.getParameters();
            Type[] genericTypes = method.getGenericParameterTypes();

            for (int i = 0; i < methodParams.length; i++) {
                Parameter param = methodParams[i];
                Type genericType = genericTypes[i];
                ParamDesc desc = param.getAnnotation(ParamDesc.class);

                String paramName = param.getName();

                // 防呆检测：没加 -parameters 编译参数时参数名会变成 arg0, arg1
                if (paramName.matches("arg\\d+")) {
                    throw new IllegalStateException(
                            "参数名为 " + paramName + "，请在 pom.xml 中添加 <parameters>true</parameters> 编译参数");
                }

                String description = (desc != null) ? desc.value() : paramName;
                boolean required = (desc != null) ? desc.required() : true;

                if (isSimpleType(param.getType())) {
                    String type = mapSimpleType(param.getType());
                    params.add(new ToolParameter(paramName, description, type, required));
                } else {
                    // 复杂类型：生成完整 JSON Schema
                    ObjectNode schema = buildSchema(param.getType(), genericType, new HashSet<>());
                    if (desc != null) {
                        schema.put("description", desc.value());
                    }
                    params.add(new ToolParameter(paramName, description, "object", required, schema));
                }
            }

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

    // ========== JSON Schema 生成 ==========

    /**
     * 递归构建 JSON Schema，支持嵌套对象、List、Map 等复杂类型。
     */
    private ObjectNode buildSchema(Class<?> type, Type genericType, Set<Class<?>> visited) {
        ObjectNode node = MAPPER.createObjectNode();

        // 简单类型
        if (isSimpleType(type)) {
            node.put("type", mapSimpleType(type));
            return node;
        }

        // 枚举
        if (type.isEnum()) {
            node.put("type", "string");
            ArrayNode enumValues = node.putArray("enum");
            for (Object c : type.getEnumConstants()) {
                enumValues.add(c.toString());
            }
            return node;
        }

        // 数组
        if (type.isArray()) {
            node.put("type", "array");
            node.set("items", buildSchema(type.getComponentType(), type.getComponentType(), visited));
            return node;
        }

        // List / Collection
        if (Collection.class.isAssignableFrom(type)) {
            node.put("type", "array");
            Type itemType = extractGenericType(genericType, 0);
            if (itemType != null) {
                node.set("items", buildSchema(getRawClass(itemType), itemType, visited));
            } else {
                node.putObject("items"); // any
            }
            return node;
        }

        // Map
        if (Map.class.isAssignableFrom(type)) {
            node.put("type", "object");
            Type valueType = extractGenericType(genericType, 1);
            if (valueType != null) {
                node.set("additionalProperties", buildSchema(getRawClass(valueType), valueType, visited));
            }
            return node;
        }

        // 复杂对象 — 递归解析字段
        if (visited.contains(type)) {
            // 防止循环引用
            node.put("type", "object");
            node.put("description", type.getSimpleName() + " (circular reference)");
            return node;
        }
        visited.add(type);

        node.put("type", "object");
        ObjectNode properties = node.putObject("properties");
        ArrayNode requiredArr = node.putArray("required");

        for (Field field : getAllFields(type)) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            String fieldName = field.getName();
            ObjectNode fieldSchema = buildSchema(field.getType(), field.getGenericType(), new HashSet<>(visited));

            // 如果字段上有 @ParamDesc，加上描述
            ParamDesc fieldDesc = field.getAnnotation(ParamDesc.class);
            if (fieldDesc != null) {
                fieldSchema.put("description", fieldDesc.value());
            }

            properties.set(fieldName, fieldSchema);
            requiredArr.add(fieldName);
        }

        visited.remove(type);
        return node;
    }

    /**
     * 获取类及其所有父类的字段。
     */
    private List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    /**
     * 从泛型类型中提取指定位置的类型参数。
     */
    private Type extractGenericType(Type type, int index) {
        if (type instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) type).getActualTypeArguments();
            if (args.length > index) {
                return args[index];
            }
        }
        return null;
    }

    /**
     * 获取 Type 的原始 Class。
     */
    private Class<?> getRawClass(Type type) {
        if (type instanceof Class) return (Class<?>) type;
        if (type instanceof ParameterizedType) return (Class<?>) ((ParameterizedType) type).getRawType();
        return Object.class;
    }

    private boolean isSimpleType(Class<?> type) {
        return type == String.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == double.class || type == Double.class
                || type == float.class || type == Float.class
                || type == boolean.class || type == Boolean.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class
                || type == BigDecimal.class || type == BigInteger.class
                || type == java.util.Date.class
                || type.getName().startsWith("java.time.");  // LocalDate, LocalDateTime, etc.
    }

    private String mapSimpleType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class) return "integer";
        if (type == long.class || type == Long.class) return "integer";
        if (type == double.class || type == Double.class) return "number";
        if (type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type == short.class || type == Short.class) return "integer";
        if (type == byte.class || type == Byte.class) return "integer";
        if (type == BigDecimal.class || type == BigInteger.class) return "number";
        if (type == java.util.Date.class || type.getName().startsWith("java.time.")) return "string";
        return "string";
    }
}
