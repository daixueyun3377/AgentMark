package io.github.daixueyun3377.agentmark.example;

import io.github.daixueyun3377.agentmark.core.annotation.ParamDesc;
import io.github.daixueyun3377.agentmark.core.annotation.AgentMark;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 示例工具类 —— 展示如何用 @AgentMark 和 @ParamDesc 注解标记方法。
 */
@Service
public class DemoToolService {

    @AgentMark(name = "查询天气", description = "查询指定城市的当前天气信息")
    public Map<String, Object> getWeather(@ParamDesc("城市名称") String city) {
        Map<String, Object> weather = new HashMap<String, Object>();
        weather.put("city", city);
        weather.put("temperature", "22°C");
        weather.put("condition", "晴");
        weather.put("humidity", "45%");
        weather.put("wind", "东风 3 级");
        return weather;
    }

    @AgentMark(name = "查询时间", description = "查询当前系统时间")
    public String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @AgentMark(name = "计算器", description = "计算两个数的四则运算")
    public Map<String, Object> calculate(
            @ParamDesc("第一个数") double a,
            @ParamDesc("运算符：+、-、*、/") String operator,
            @ParamDesc("第二个数") double b) {
        double result;
        switch (operator) {
            case "+": result = a + b; break;
            case "-": result = a - b; break;
            case "*": result = a * b; break;
            case "/": result = b != 0 ? a / b : Double.NaN; break;
            default: throw new IllegalArgumentException("不支持的运算符: " + operator);
        }
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("expression", a + " " + operator + " " + b);
        map.put("result", result);
        return map;
    }
}
