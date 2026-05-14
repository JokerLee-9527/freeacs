# Free ACS

![Freeacs Logo](https://github.com/freeacs/readme/blob/master/logo.png)
 
![main](https://github.com/freeacs/freeacs/actions/workflows/maven.yml/badge.svg?branch=master)
[![Maintainability](https://api.codeclimate.com/v1/badges/4b32968512c546806799/maintainability)](https://codeclimate.com/github/freeacs/freeacs/maintainability)
[![CodeQL Analysis](https://github.com/freeacs/freeacs/actions/workflows/codeql.yml/badge.svg)](https://github.com/freeacs/freeacs/actions/workflows/codeql.yml)

FreeACS 是一个相当完整的 TR-069 ACS（自动配置服务器），基于 MIT 许可证免费提供。您可以下载安装，或为项目做出贡献！

## 前置要求

FreeACS 需要 Java 17 和 MySQL。已测试可在 Java 17、18 和 19 以及最新版本的 MySQL 上运行（后者在安装脚本中有一些小问题）。

## 讨论

* 讨论区：https://github.com/freeacs/freeacs/discussions
* [Freeforums]（已弃用）

## 构建

在 Unix/Linux 系统上使用 Maven 构建 FreeACS：

```bash
$ ./mvnw test
```

打包为可部署的 zip 文件：

```bash
$ ./mvnw package
```

可部署的 zip 文件位于 distribution 模块的 target 文件夹或各模块的 target 文件夹中。

## 贡献

请阅读 [CONTRIBUTING.md](https://github.com/freeacs/freeacs/blob/master/CONTRIBUTING.md) 了解如何设置开发环境来构建 FreeACS。

## 版本控制

我们使用 SemVer 进行版本控制。

## 许可证

本项目基于 MIT 许可证授权。

## 活跃项目成员

* **Jarl André Hübenthal (@jarlah)**

更多信息请参见 https://github.com/freeacs/freeacs/wiki/About。
