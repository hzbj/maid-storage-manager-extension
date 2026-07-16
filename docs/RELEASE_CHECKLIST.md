# 发布检查清单

## 发布前

- [ ] `gradle.properties`、`mods.toml`、README 兼容表和更新日志中的版本一致。
- [ ] 使用 JDK 17 运行 `.\gradlew.bat clean build --warning-mode all`。
- [ ] GitHub Actions 的 `Build` 工作流通过。
- [ ] 在实际客户端与专用服务器各完成一次启动验证。
- [ ] 检查普通安装不需要 `sources` JAR，成品内不含上游依赖 JAR。
- [ ] 将 `CHANGELOG.md` 当前版本压缩成面向玩家的 Release Notes。
- [ ] 检查 README 下载入口、Issue 链接和第三方声明。

## GitHub Release

- [ ] 创建带 `v` 前缀的标签，例如 `v1.2.3`。
- [ ] Release 标题使用 `女仆仓管扩展 1.2.3`。
- [ ] 上传 `1.20.1-maid_storage_manager_extension-1.2.3.jar`。
- [ ] 不上传本地依赖 JAR；`sources` JAR仅在确有开发者需求时提供。
- [ ] 写明 Minecraft、Forge 和所有必需依赖的兼容范围。
- [ ] 写明这是非官方扩展，并把问题反馈链接指向本仓库。

## 宣发前

- [ ] GitHub Release 可以在未登录状态正常下载。
- [ ] 用干净实例按 README 从零安装一次。
- [ ] 截图或演示视频与当前版本行为一致。
- [ ] 礼貌告知 Maid Storage Manager 原作者；不请求背书，不把支持压力转给上游。
