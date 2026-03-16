package io.agentmark.example;

import io.agentmark.core.annotation.AgentMark;
import io.agentmark.core.annotation.ParamDesc;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 示例工具类 —— 展示 @AgentMark 和 @ParamDesc 注解的用法。
 * 不加 @ParamDesc 时，AI 根据参数名和类型自动推断。
 */
@Service
public class DemoToolService {

    @AgentMark(name = "查询天气", description = "查询指定城市的当前天气信息")
    public Map<String, Object> getWeather(String city) {
        Map<String, Object> result = new HashMap<>();
        result.put("city", city);
        result.put("temperature", "22°C");
        result.put("condition", "晴");
        result.put("humidity", "45%");
        result.put("wind", "东风 3 级");
        return result;
    }

    @AgentMark(name = "查询时间", description = "查询当前系统时间")
    public String getCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    @AgentMark(name = "计算器", description = "计算两个数的四则运算")
    public Map<String, Object> calculate(
            double a,
            @ParamDesc("运算符：+、-、*、/") String operator,
            double b) {
        double result;
        switch (operator) {
            case "+": result = a + b; break;
            case "-": result = a - b; break;
            case "*": result = a * b; break;
            case "/": result = b != 0 ? a / b : Double.NaN; break;
            default: throw new IllegalArgumentException("不支持的运算符: " + operator);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("expression", a + " " + operator + " " + b);
        map.put("result", result);
        return map;
    }

    // ========== 复杂嵌套类型示例 ==========

    /**
     * 创建订单 —— 演示复杂嵌套参数（List + 嵌套对象）。
     * AI 会自动理解 OrderRequest 的结构，用户用自然语言描述即可。
     */
    @AgentMark(name = "创建订单", description = "根据用户需求创建订单，支持多个商品项")
    public Map<String, Object> createOrder(OrderRequest order) {
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", "ORD-" + System.currentTimeMillis());
        result.put("customerName", order.getCustomerName());
        result.put("itemCount", order.getItems() != null ? order.getItems().size() : 0);
        result.put("status", "已创建");

        double total = 0;
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                total += item.getPrice() * item.getQuantity();
            }
        }
        result.put("totalAmount", total);
        return result;
    }

    /** 订单请求 */
    public static class OrderRequest {
        private String customerName;
        private String shippingAddress;
        private List<OrderItem> items;

        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public String getShippingAddress() { return shippingAddress; }
        public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
        public List<OrderItem> getItems() { return items; }
        public void setItems(List<OrderItem> items) { this.items = items; }
    }

    /** 订单项 */
    public static class OrderItem {
        private String productName;
        private int quantity;
        private double price;

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
    }
}
