# 参与贡献

感谢你愿意帮助改进女仆仓管扩展。这个项目是非官方社区扩展，请把扩展自身的问题留在本仓库处理，不要把扩展引发的错误提交给 Maid Storage Manager 或 Touhou Little Maid 的维护者。

## 提交问题

- 使用对应的 Issue 表单，并先搜索是否已有相同问题。
- 提供完整 Mod 版本、运行环境、复现步骤、`latest.log` 和崩溃报告。
- 移除本扩展后仍能复现的问题，可以在本仓库先做来源确认；确认属于上游后再按上游规则报告。

## 本地开发

需要 JDK 17。Windows PowerShell：

```powershell
.\gradlew.bat clean build
```

构建必须完成编译、测试和重映射，成品位于 `build/libs/`。公开 Maven 能获取的第三方 JAR 不应提交到 `libs/`；当前唯一例外见 `libs/README.md`。

## Pull Request

- 一个 PR 只处理一个清晰的问题。
- 机制改动应来自现有代码、上游接口或可复现行为，不要加入未经验证的假设性兼容逻辑。
- 必要处添加少量维护注释，并为错误修复补充回归测试。
- 提交前运行 `clean build`，在 PR 中说明验证结果和仍未覆盖的场景。
- 不要提交构建产物、运行目录、日志、IDE 配置或公开 Maven 依赖 JAR。

提交代码即表示你有权贡献相关内容，并同意代码按 MIT、项目纹理按 `CC BY-NC-SA 4.0` 发行。
