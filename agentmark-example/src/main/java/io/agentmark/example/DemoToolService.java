package io.agentmark.example;

import io.agentmark.core.annotation.P;
import io.agentmark.core.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 示例工具类 —— 展示如何用 @Tool 和 @P 注解标记方法。
 */
@Service
public class DemoToolService {

    @Tool(name = "查询天气", description = "查询指定城市的当前天气信息")
    public Map<String, Object> getWeather(@P("城市名称") String city) {
        // 模拟返回天气数据
        return Map.of(
                "city", city,
                "temperature", "22°C",
                "condition", "晴",
                "humidity", "45%",
                "wind", "东风 3 级"
        );
    }

    @Tool(name = "查询时间", description = "查询当前系统时间")
    public String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Tool(name = "计算器", description = "计算两个数的四则运算")
    public Map<String, Object> calculate(
            @P("第一个数") double a,
            @P("运算符：+、-、*、/") String operator,
            @P("第二个数") double b) {
        double result = switch (operator) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> b != 0 ? a / b : Double.NaN;
            default -> throw new IllegalArgumentException("不支持的运算符: " + operator);
        };
        return Map.of("expression", a + " " + operator + " " + b, "result", result);
    }
}
