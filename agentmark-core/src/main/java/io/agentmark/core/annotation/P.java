package io.agentmark.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 描述工具方法的参数信息，供 AI 理解参数含义。
 *
 * <pre>
 * {@code
 * @Tool(name = "查询订单", description = "根据订单号查询订单详情")
 * public Order getOrder(@P("订单号") String orderId) { ... }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface P {

    /** 参数描述 */
    String value();

    /** 是否必填，默认 true */
    boolean required() default true;
}
