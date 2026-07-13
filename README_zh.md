# Tom's Simple Storage 1.7.10 Port

[English](README.md)

## 项目简介

Tom's Simple Storage 1.7.10 Port 是将高版本 Tom's Simple Storage 模组移植到 Minecraft 1.7.10 的低版本移植项目。

本模组提供轻量的集中存储网络，让玩家可以通过终端访问连接容器中的物品，并在 1.7.10 环境中获得接近高版本 Tom's Simple Storage 的使用体验。

## 必需依赖

- NotEnoughItems
- UniMixins

NEI 集成使用 GTNH NEI API 和客户端 Mixin。请使用本模组构建所依赖的 GTNH NotEnoughItems 分支，或提供 `AutoCraftingManager` 与 `DefaultOverlayHandler` 的兼容版本。

## 主要功能

- 使用存储驱动器连接相邻容器，建立集中存储网络。
- 使用存储桥接方块扩展容器连接范围。
- 使用存储终端集中查看、存入和取出网络中的物品。
- 使用合成终端在访问存储网络的同时进行物品合成。
- 使用无线终端在一定范围内远程访问已绑定的终端。
- 支持与 NEI 配合进行配方查看、配方填充和自动合成。
- 可选兼容 NotEnoughCharacters，增强中文和拼音搜索体验。

## 许可证

本项目使用 GPL-3.0 许可证。
