package io.agentmark.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为 AI Agent 可调用的工具。
 * AI 会自动分析方法签名和参数类型，理解如何调用。
 *
 * <pre>
 * {@code
 * @AgentMark(name = "查询天气", description = "查询指定城市的当前天气")
 * public WeatherInfo getWeather(String city) {
 *     return weatherApi.query(city);
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentMark {

    /** 工具名称，为空时自动使用方法名 */
    String name() default "";

    /** 工具描述，为空时自动生成 */
    String description() default "";
}
