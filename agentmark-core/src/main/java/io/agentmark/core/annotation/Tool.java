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
 * @Tool(name = "查询天气", description = "查询指定城市的当前天气")
 * public WeatherInfo getWeather(@P("城市名称") String city) {
 *     return weatherApi.query(city);
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {

    /** 工具名称，AI 用来识别和选择工具 */
    String name();

    /** 工具描述，帮助 AI 理解何时该调用此工具 */
    String description();
}
