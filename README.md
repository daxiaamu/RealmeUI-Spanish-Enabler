# RealmeUI Spanish Enabler

一款为已取得 ROOT 权限的 RealmeUI / ColorOS 设备启用西班牙语的轻量 Android 应用。

部分中国版设备的语言设置界面会隐藏西班牙语，即使系统中仍然保留了相应语言资源。本应用可以直接将系统语言切换为 `es-ES`，并支持恢复 ROM 默认语言。

![应用界面](docs/screenshot.png)

## 功能

- 一键启用西班牙语 `es-ES`
- 一键恢复 ROM 默认语言
- 支持即时生效，无需重启
- 即时切换失败时自动写入系统设置，可重启后生效
- 提供安全确认后的重启按钮
- 界面支持中文、English 和 Español
- 支持 Magisk、KernelSU、APatch 等 ROOT 方案

## 下载

请从 [Releases](https://github.com/daxiaamu/RealmeUI-Spanish-Enabler/releases) 下载最新 APK。

## 使用要求

- Android 8.0 或更高版本
- 设备已取得 ROOT 权限
- ROM 中仍保留西班牙语资源；缺失的系统翻译可能回退到中文或英文

## 实现原理

应用取得 ROOT 后为自身授予 `android.permission.CHANGE_CONFIGURATION`，然后调用 ActivityManager 的系统配置接口即时更新语言。若普通应用进程受到隐藏 API 限制，会通过 ROOT `app_process` 助手再次尝试。两种即时方式都失败时，应用仍会写入：

```shell
settings --user current put system system_locales es-ES
```

该兜底方式需要重启后才能完整生效。

## 构建

需要 JDK 17、Android SDK 37 和 Gradle 9.4.1：

```shell
./gradlew assembleDebug
```

生成文件位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 已验证设备

- 真我 GT 8
- RealmeUI 7.0

实测 `zh-CN → es-ES` 和 `es-ES → zh-CN` 均可在不重启的情况下即时生效。

## 注意

切换系统语言属于高权限操作。请确认设备已备份重要数据，并只从本仓库下载 APK。

开发者：大侠阿木（[daxiaamu.com](https://daxiaamu.com)）
