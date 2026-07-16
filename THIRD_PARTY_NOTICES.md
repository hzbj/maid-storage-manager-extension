# 第三方项目与素材声明

本文件记录女仆仓管扩展使用的上游项目、构建依赖和素材许可。这里的项目名称仅用于说明兼容关系，不表示上游作者对本扩展提供官方支持或背书。

## Maid Storage Manager（女仆仓管）

- 作者：xypp
- 源码：https://github.com/zxy19/maid_storage_manager
- 发行页：https://modrinth.com/mod/maid-storage-manager
- 许可证：MIT
- 本项目锁定的开发/运行依赖：Minecraft 1.20.1 Forge 版 `1.15.6`，Modrinth 版本 ID `xTNPlwGP`

部分兼容接缝和稳定性实现依据 1.15.6 的公开源码、API 与运行行为编写。扩展成品不包含或覆盖 Maid Storage Manager 的 JAR、class 或原始资源。

## Touhou Little Maid（车万女仆）

- 作者及维护团队：TartaricAcid 与项目贡献者
- 源码：https://github.com/TartaricAcid/TouhouLittleMaid
- 发行页：https://modrinth.com/mod/touhou-little-maid
- 许可证：代码为 MIT；上游资源为 CC BY-NC-SA 4.0
- 本项目锁定的开发/运行依赖：Minecraft 1.20.1 Forge 版 `1.5.2`，Modrinth 版本 ID `YpdxfSC2`

扩展成品不包含或覆盖 Touhou Little Maid 的 JAR、class 或原始资源。

## Touhou Little Maid: Spell（车万女仆：魔法皆通）

- 作者：yimeng261
- 源码：https://github.com/yimeng261/Touhou-Little-Maid-Spell
- 发行页：https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid-spell
- 许可证：MIT

本项目在运行时要求该 Mod 提供末影腰包能力；该依赖不会被打包进扩展成品。

## Tour Guide

- 作者：xypp
- 发行页：https://www.curseforge.com/minecraft/mc-mods/tour-guide
- 项目 ID：`1380389`
- 许可证：MIT
- 本地构建依赖：Minecraft 1.20.1 Forge 版 `1.0.2`

Tour Guide 仅用于解析女仆仓管的编译期类型，未被声明为本扩展的直接运行依赖，也不会被打包进扩展成品。保留本地 JAR 的原因和校验值见 `libs/README.md`。

## 本项目纹理

`tools/art/generate_logistics_pixel_art.py` 记录了部分物品纹理的制作过程：轮廓选自内置图像生成的概念草案，并参考 Maid Storage Manager 1.15.6 的视觉语言逐像素重绘。为避免对素材独立性作过度声明，本项目将自身 PNG 纹理统一按 `CC BY-NC-SA 4.0` 发布，具体范围见 `LICENSE-ASSETS.md`。

Minecraft、Forge 以及上述项目的商标与名称归各自权利人所有。
