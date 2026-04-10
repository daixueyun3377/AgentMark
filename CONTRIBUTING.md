# Contributing to AgentMark

感谢你对 AgentMark 的关注！欢迎任何形式的贡献。

## 如何贡献

### 报告 Bug

请通过 [GitHub Issues](https://github.com/daixueyun3377/AgentMark/issues/new?template=bug_report.yml) 提交 Bug 报告，包含：

- AgentMark 版本
- JDK 版本和 Spring Boot 版本
- 最小可复现代码
- 期望行为 vs 实际行为

### 功能建议

请通过 [GitHub Issues](https://github.com/daixueyun3377/AgentMark/issues/new?template=feature_request.yml) 提交功能建议。

### 提交代码

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/your-feature`
3. 提交更改：`git commit -m 'feat: 你的功能描述'`
4. 推送分支：`git push origin feature/your-feature`
5. 创建 Pull Request

### Commit 规范

使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式：

- `feat:` 新功能
- `fix:` Bug 修复
- `docs:` 文档更新
- `refactor:` 代码重构
- `chore:` 构建/工具变更

### 开发环境

- JDK 1.8+
- Maven 3.6+
- 编译参数需开启 `-parameters`

```bash
# 克隆并编译
git clone https://github.com/daixueyun3377/AgentMark.git
cd AgentMark
mvn clean install -DskipTests

# 运行示例
mvn spring-boot:run -pl agentmark-example
```

## 联系方式

- 📧 Email: [daixueyun3377@gmail.com](mailto:daixueyun3377@gmail.com)
- 🐛 Issues: [GitHub Issues](https://github.com/daixueyun3377/AgentMark/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/daixueyun3377/AgentMark/discussions)

## License

贡献的代码将遵循 [Apache License 2.0](LICENSE)。
