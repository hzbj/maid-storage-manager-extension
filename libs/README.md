# 本地构建依赖

公开可通过 Maven 获取的依赖不应放入此目录。女仆仓管与车万女仆已经在 `build.gradle` 中通过 Modrinth Maven 的精确版本 ID 获取。

当前只保留以下无法可靠通过公开 Maven 验证的编译期依赖：

| 文件 | 来源 | 用途 | SHA-256 |
| --- | --- | --- | --- |
| `1.20.1-tour_guide-1.0.2.jar` | [CurseForge：Tour Guide](https://www.curseforge.com/minecraft/mc-mods/tour-guide)，项目 ID `1380389` | 解析女仆仓管使用的 Tour Guide 类型；`compileOnly` | `cdc05214ff9a863d2057bafc4350ec613c95f646c94fa598fa892e67a77bdd26` |

该文件由 xypp 发布，CurseForge 项目标注为 MIT License。它不会被打包进女仆仓管扩展成品。
