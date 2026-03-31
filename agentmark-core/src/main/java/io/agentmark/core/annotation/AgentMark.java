package io.agentmark.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为 AI Agent 可调用的工具。
 *
 * <pre>
 * {@code
 * @AgentMark(name = "查询天气", description = "查询指定城市的当前天气")
 * public WeatherInfo getWeather(@ParamDesc("城市名称") String city) {
 *     return weatherApi.query(city);
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentMark {

    /** 工具名称，为空时使用方法名 */
    String name() default "";

    /** 工具描述，为空时 AI 根据方法签名自动推断 */
    String description() default "";
}
