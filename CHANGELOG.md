# Changelog

所有重要变更记录在此文件中。格式基于 [Keep a Changelog](https://keepachangelog.com/)。

## [1.0.0] - 2026-04-10

### 🎉 首个正式版本

AgentMark v1.0.0 — 一个注解，让 AI Agent 调用你的 Java 方法。

### ✨ 核心功能

- **@AgentMark 注解** — 标记方法为 AI 可调用的工具，name 仅支持英文和下划线
- **@ParamDesc 注解** — 可选，为参数添加描述信息，支持标注在方法参数和 POJO 字段上
- **复杂类型支持** — 自动将 Java 类型转换为 JSON Schema，支持嵌套对象、List、Set、Map、数组、枚举、循环引用检测
- **Java 8 日期类型** — 支持 LocalDate、LocalDateTime、LocalTime、Instant、OffsetDateTime、ZonedDateTime
- **多模型支持** — 内置 Claude（Anthropic）和 OpenAI Provider，通过 OpenAI 兼容接口支持通义千问、DeepSeek、Moonshot 等
- **多轮对话** — AgentMarkSession 支持带上下文记忆的多轮对话
- **自动工具编排** — 复杂任务自动拆解为多个工具调用，最多 10 轮

### 🚀 Spring Boot 集成

- **Spring Boot Starter** — 引入依赖即自动扫描注册 @AgentMark 工具
- **SmartInitializingSingleton** — 所有单例 Bean 初始化完成后再扫描，避免循环依赖
- **配置元数据** — IDE 自动补全 agentmark.* 配置项（provider、api-key、model、base-url）
- **自定义 Provider** — 实现 ModelProvider 接口即可对接任意 LLM

### 📦 依赖与兼容性

- JDK 1.8+，兼容 Spring Boot 2.7+ 和 3.x
- jackson-databind 2.17.0 + jackson-datatype-jsr310（JavaTimeModule）
- OkHttp 4.12.0（显式声明版本，避免传递依赖冲突）
- SLF4J 版本由 Spring Boot BOM 统一管理，不再强制指定

### 📖 文档

- README 包含完整 import 速查、API 速查表、多模块集成指南
- Javadoc 自动发布到 GitHub Pages
- CONTRIBUTING.md 贡献指南
- SECURITY.md 安全漏洞报告流程
- GitHub Issue 模板（Bug 报告 / 功能建议）
- GitHub Discussions 已开启

[1.0.0]: https://github.com/daixueyun3377/AgentMark/releases/tag/v1.0.0
