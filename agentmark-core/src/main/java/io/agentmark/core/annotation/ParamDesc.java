package io.agentmark.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 可选注解：为参数添加额外描述，帮助 AI 更好地理解参数含义。
 * 不加此注解时，AI 会根据参数名和类型自动推断。
 *
 * <pre>
 * {@code
 * @AgentMark(name = "查询订单", description = "根据订单号查询订单详情")
 * public Order getOrder(@ParamDesc("订单号，格式如 ORD-20260316") String orderId) { ... }
 * }
 * </pre>
 */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ParamDesc {

    /** 参数描述 */
    String value();

    /** 是否必填，默认 true */
    boolean required() default true;
}
